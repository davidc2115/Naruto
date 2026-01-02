package com.narutoai.chat.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.stringPreferencesKey
import com.narutoai.chat.data.apiKeysDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Client pour l'API Groq Vision (analyse d'images)
 * Sélectionne dynamiquement un modèle Vision disponible (préférence non-Llama).
 */
class GroqVisionClient(private val context: Context) {
    
    companion object {
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_MODELS_URL = "https://api.groq.com/openai/v1/models"

        // Fallback si l'endpoint models est indisponible.
        // IMPORTANT: ne pas inclure de modèles décommissionnés.
        // Modèles vision "préférés" (on les tente en premier si disponibles).
        // NB: Groq peut changer les IDs -> on tolère model_not_found et on fallback.
        private val PREFERRED_VISION_MODELS = listOf(
            "qwen-2.5-vl-72b-instruct",
            "qwen2.5-vl-72b-instruct",
            "pixtral-12b-2409",
            "pixtral-12b",
            "llava-1.6-34b"
        )

        private val FALLBACK_VISION_MODELS = listOf(
            // Liste la plus compatible possible (Groq peut changer)
            "llama-3.3-70b-vision",
            "llama-3.3-70b-vision-preview",
            "llama-3.2-90b-vision",
            "llama-3.2-90b-vision-preview"
        )
        // Réduire un peu la taille pour éviter les erreurs 400 (payload trop gros).
        private const val MAX_IMAGE_SIZE_KB = 350 // 350KB max (avant Base64)
    }
    
    /**
     * Charge une clé API depuis DataStore (même système que ApiKeyManager)
     * Séparateur de clés: "|||"
     */
    private suspend fun getApiKey(): String {
        return try {
            android.util.Log.d("GroqVision", "🔍 Chargement clés API depuis DataStore...")
            
            val apiKeysKey = stringPreferencesKey("api_keys")
            
            val keysString = context.apiKeysDataStore.data
                .map { preferences -> preferences[apiKeysKey] ?: "" }
                .first()
            
            android.util.Log.d("GroqVision", "📦 Données DataStore brutes: '${keysString.take(50)}${if (keysString.length > 50) "..." else ""}'")
            
            if (keysString.isNotEmpty()) {
                // Séparer les clés avec "|||" (même séparateur que ApiKeyManager)
                val keys = keysString.split("|||").filter { it.isNotBlank() }
                android.util.Log.d("GroqVision", "✅ ${keys.size} clé(s) API trouvée(s) après parsing")
                
                if (keys.isNotEmpty()) {
                    val firstKey = keys.first()
                    android.util.Log.d("GroqVision", "🔑 Utilisation clé: ${firstKey.take(12)}...${firstKey.takeLast(4)}")
                    return firstKey
                } else {
                    android.util.Log.w("GroqVision", "⚠️ Parsing a donné 0 clés (données vides après split)")
                }
            } else {
                android.util.Log.w("GroqVision", "⚠️ DataStore vide (keysString.isEmpty())")
            }
            
            android.util.Log.e("GroqVision", "❌ Aucune clé API Groq trouvée dans DataStore")
            ""
        } catch (e: Exception) {
            android.util.Log.e("GroqVision", "❌ EXCEPTION chargement clés: ${e.javaClass.simpleName}: ${e.message}", e)
            e.printStackTrace()
            ""
        }
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedVisionModels: List<String>? = null

    private val badVisionModels = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun isBannedModel(modelId: String): Boolean {
        // 11b vision a été régulièrement décommissionné côté Groq (et provoque des 400/404).
        return modelId.contains("llama-3.2-11b-vision", ignoreCase = true)
    }

    private fun isModelDecommissionedError(httpBody: String?): Boolean {
        return try {
            if (httpBody.isNullOrBlank()) return false
            val obj = JSONObject(httpBody)
            val err = obj.optJSONObject("error") ?: return false
            val code = err.optString("code", "")
            val message = err.optString("message", "")
            code.contains("model_decommissioned", ignoreCase = true) ||
                message.contains("decommissioned", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun getGroqErrorCode(httpBody: String?): String? {
        return try {
            if (httpBody.isNullOrBlank()) return null
            val obj = JSONObject(httpBody)
            val err = obj.optJSONObject("error") ?: return null
            err.optString("code", null)
        } catch (_: Exception) {
            null
        }
    }

    private fun getGroqErrorMessage(httpBody: String?): String? {
        return try {
            if (httpBody.isNullOrBlank()) return null
            val obj = JSONObject(httpBody)
            val err = obj.optJSONObject("error") ?: return null
            err.optString("message", null)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchVisionModels(apiKey: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(GROQ_MODELS_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@withContext emptyList()
                }

                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return@withContext emptyList()
                val ids = buildList {
                    for (i in 0 until data.length()) {
                        val id = data.optJSONObject(i)?.optString("id").orEmpty()
                        if (id.isNotBlank()) add(id)
                    }
                }

                // Garder uniquement les modèles vision (vision/vl)
                val vision = ids
                    .filter { it.contains("vision", ignoreCase = true) || it.contains("-vl", ignoreCase = true) }
                    .filterNot { isBannedModel(it) }
                    .distinct()

                // Trier: préférer non-llama, puis plus gros, puis preview
                val sorted = vision.sortedWith(
                    compareByDescending<String> { !it.contains("llama", ignoreCase = true) }
                        .thenByDescending { it.contains("90b", ignoreCase = true) || it.contains("72b", ignoreCase = true) || it.contains("70b", ignoreCase = true) }
                        .thenByDescending { it.contains("preview", ignoreCase = true) }
                        .thenBy { it }
                )

                sorted
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun getVisionModelCandidates(apiKey: String): List<String> {
        cachedVisionModels?.let { if (it.isNotEmpty()) return it }
        val fetched = fetchVisionModels(apiKey)
        val models = (PREFERRED_VISION_MODELS + fetched + FALLBACK_VISION_MODELS)
            .distinct()
            .filterNot { isBannedModel(it) }
            .filterNot { badVisionModels.contains(it) }
        cachedVisionModels = models
        return models
    }
    
    /**
     * Analyse une image et génère un descriptif physique détaillé
     */
    suspend fun analyzePhotoForCharacter(imageUri: Uri): Result<PhysicalDescription> {
        return withContext(Dispatchers.IO) {
            try {
                // Charger et compresser l'image
                val base64Image = loadAndCompressImage(imageUri)
                    ?: return@withContext Result.failure(Exception("Impossible de charger l'image"))
                
                // Créer le prompt pour l'analyse
                val prompt = """
Analyse cette photo et fournis une description physique détaillée en français pour créer un personnage de fiction.

FORMAT REQUIS (réponds UNIQUEMENT avec ce JSON, rien d'autre):
{
  "age": "estimation d'âge ou tranche (ex: 18-25 ans, jeune adulte, mature)",
  "gender": "homme/femme/autre",
  "hairColor": "couleur et style des cheveux (ex: blonds longs, noirs courts)",
  "eyeColor": "couleur des yeux",
  "skinTone": "teint de peau (clair, mat, foncé, etc.)",
  "bodyType": "type de corps (athlétique, mince, musclé, etc.)",
  "height": "estimation taille (ex: petite ~160cm, moyenne ~170cm, grande ~180cm)",
  "facialFeatures": "traits du visage remarquables",
  "distinctiveFeatures": "signes distinctifs (tatouages, cicatrices, etc.)",
  "detailedDescription": "description physique complète en 2-3 phrases"
}

IMPORTANT:
- Réponds UNIQUEMENT avec le JSON, sans texte avant ou après.
- Le personnage doit être ADULTE (18+). Si l'âge est incertain, renvoie 21+.
                """.trimIndent()
                
                // Construire la requête JSON
                fun buildRequestJson(model: String): JSONObject {
                    return JSONObject().apply {
                        put("model", model)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", JSONArray().apply {
                                    // Text prompt
                                    put(JSONObject().apply {
                                        put("type", "text")
                                        put("text", prompt)
                                    })
                                    // Image
                                    put(JSONObject().apply {
                                        put("type", "image_url")
                                        put("image_url", JSONObject().apply {
                                            put("url", "data:image/jpeg;base64,$base64Image")
                                        })
                                    })
                                })
                            })
                        })
                        put("temperature", 0.3)
                        put("max_tokens", 1000)
                    }
                }
                
                // Créer la requête HTTP
                val apiKey = getApiKey()
                if (apiKey.isEmpty()) {
                    android.util.Log.e("GroqVision", "❌ ERREUR CRITIQUE: Aucune clé API Groq trouvée dans DataStore")
                    android.util.Log.e("GroqVision", "   Allez dans Paramètres > Section 'Clés API Groq' > Bouton 'Ajouter une clé Groq'")
                    android.util.Log.e("GroqVision", "   Les clés doivent commencer par 'gsk_'")
                    return@withContext Result.failure(
                        Exception("❌ Clé API Groq non trouvée\n\nVérifiez que vous avez bien ajouté au moins une clé dans :\nParamètres > Clés API Groq > Ajouter\n\nLes clés doivent commencer par 'gsk_'")
                    )
                }
                
                var lastHttpError: Pair<Int, String?>? = null
                var responseBody: String? = null
                var usedModel: String? = null
                var success = false

                val modelCandidates = getVisionModelCandidates(apiKey)
                    .filterNot { isBannedModel(it) }
                    .ifEmpty { FALLBACK_VISION_MODELS.filterNot { m -> isBannedModel(m) } }

                android.util.Log.d(
                    "GroqVision",
                    "🧠 Vision candidates=${modelCandidates.size} first=${modelCandidates.take(5)}"
                )

                for (model in modelCandidates) {
                    usedModel = model
                    val requestBody = buildRequestJson(model).toString()
                        .toRequestBody("application/json".toMediaType())

                    val request = Request.Builder()
                        .url(GROQ_API_URL)
                        .header("Authorization", "Bearer $apiKey")
                        .header("Content-Type", "application/json")
                        .post(requestBody)
                        .build()

                    android.util.Log.d("GroqVision", "🧠 Vision model: $model, payload=${requestBody.contentLength()} bytes")

                    val response = client.newCall(request).execute()
                    responseBody = response.body?.string()

                    if (response.isSuccessful) {
                        success = true
                        lastHttpError = null
                        break
                    }

                    lastHttpError = response.code to responseBody
                    android.util.Log.e("GroqVision", "HTTP ${response.code} (model=$model): $responseBody")

                    val errorCode = getGroqErrorCode(responseBody)
                    val errorMessage = getGroqErrorMessage(responseBody).orEmpty()

                    // Si le modèle est décommissionné / introuvable, on tente le fallback (même si le status != 400)
                    val isModelGone =
                        (errorCode != null && (errorCode.contains("model_decommissioned", true) || errorCode.contains("model_not_found", true))) ||
                            errorMessage.contains("decommissioned", ignoreCase = true) ||
                            errorMessage.contains("model", ignoreCase = true) && errorMessage.contains("not found", ignoreCase = true) ||
                            errorMessage.contains("does not exist", ignoreCase = true)

                    if (isModelGone) {
                        badVisionModels.add(model)
                        cachedVisionModels = null
                        continue
                    }

                    // Sur 400 on tente le fallback; sinon on arrête.
                    if (response.code != 400) {
                        return@withContext Result.failure(
                            Exception("Erreur API Groq Vision: HTTP ${response.code}\n${responseBody?.take(500) ?: ""}")
                        )
                    }

                    // Si le modèle est décommissionné, invalider le cache (on relira /models au prochain run)
                    if (isModelDecommissionedError(responseBody)) {
                        cachedVisionModels = null
                    }
                }

                if (responseBody == null) {
                    return@withContext Result.failure(Exception("Réponse vide (Groq Vision)"))
                }
                if (!success && lastHttpError != null && lastHttpError.first == 400) {
                    return@withContext Result.failure(
                        Exception(
                            buildString {
                                append("Erreur API Groq Vision: HTTP 400")
                                if (!usedModel.isNullOrBlank()) append("\nModèle tenté: $usedModel")
                                append("\nCandidats (top5): ${modelCandidates.take(5)}")
                                append("\n")
                                append(lastHttpError.second?.take(800) ?: "")
                            }
                        )
                    )
                }
                if (!success) {
                    return@withContext Result.failure(
                        Exception(
                            buildString {
                                append("Erreur API Groq Vision: HTTP ${lastHttpError?.first ?: "?"}")
                                if (!usedModel.isNullOrBlank()) append("\nModèle tenté: $usedModel")
                                append("\nCandidats (top5): ${modelCandidates.take(5)}")
                                append("\n")
                                append(lastHttpError?.second?.take(800) ?: "")
                            }
                        )
                    )
                }
                
                // Parser la réponse
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")
                
                if (choices.length() == 0) {
                    return@withContext Result.failure(Exception("Aucune réponse générée"))
                }
                
                val content = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                
                android.util.Log.d("GroqVision", "Réponse brute: $content")
                
                // Extraire le JSON de la réponse
                val jsonContent = extractJsonFromResponse(content)
                val analysisJson = JSONObject(jsonContent)
                
                // Créer l'objet PhysicalDescription
                val description = PhysicalDescription(
                    age = analysisJson.optString("age", ""),
                    gender = analysisJson.optString("gender", ""),
                    hairColor = analysisJson.optString("hairColor", ""),
                    eyeColor = analysisJson.optString("eyeColor", ""),
                    skinTone = analysisJson.optString("skinTone", ""),
                    bodyType = analysisJson.optString("bodyType", ""),
                    height = analysisJson.optString("height", ""),
                    facialFeatures = analysisJson.optString("facialFeatures", ""),
                    distinctiveFeatures = analysisJson.optString("distinctiveFeatures", ""),
                    detailedDescription = analysisJson.optString("detailedDescription", "")
                )
                
                Result.success(description)
                
            } catch (e: Exception) {
                android.util.Log.e("GroqVision", "Erreur analyse: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Charge et compresse une image en Base64
     */
    private fun loadAndCompressImage(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            // Redimensionner si trop grande
            val maxDimension = 1024
            if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val newWidth: Int
                val newHeight: Int
                
                if (ratio > 1) {
                    newWidth = maxDimension
                    newHeight = (maxDimension / ratio).toInt()
                } else {
                    newHeight = maxDimension
                    newWidth = (maxDimension * ratio).toInt()
                }
                
                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            }
            
            // Compresser en JPEG
            val outputStream = ByteArrayOutputStream()
            var quality = 85
            
            do {
                outputStream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                quality -= 10
            } while (outputStream.size() > MAX_IMAGE_SIZE_KB * 1024 && quality > 20)
            
            val imageBytes = outputStream.toByteArray()
            outputStream.close()
            
            android.util.Log.d("GroqVision", "Image compressée: ${imageBytes.size / 1024}KB, qualité: $quality")
            
            Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            
        } catch (e: Exception) {
            android.util.Log.e("GroqVision", "Erreur compression: ${e.message}", e)
            null
        }
    }
    
    /**
     * Extrait le JSON de la réponse (au cas où il y aurait du texte avant/après)
     */
    private fun extractJsonFromResponse(response: String): String {
        // Chercher le JSON entre { et }
        val startIndex = response.indexOf('{')
        val endIndex = response.lastIndexOf('}')
        
        return if (startIndex >= 0 && endIndex > startIndex) {
            response.substring(startIndex, endIndex + 1)
        } else {
            response // Si pas de { }, on essaye tel quel
        }
    }
}

/**
 * Résultat de l'analyse physique
 */
data class PhysicalDescription(
    val age: String,
    val gender: String,
    val hairColor: String,
    val eyeColor: String,
    val skinTone: String,
    val bodyType: String,
    val height: String,
    val facialFeatures: String,
    val distinctiveFeatures: String,
    val detailedDescription: String
) {
    /**
     * Génère une description physique complète formatée
     */
    fun toFormattedDescription(): String {
        return buildString {
            if (detailedDescription.isNotEmpty()) {
                appendLine(detailedDescription)
                appendLine()
            }
            
            if (age.isNotEmpty()) appendLine("Âge: $age")
            if (height.isNotEmpty()) appendLine("Taille: $height")
            if (hairColor.isNotEmpty()) appendLine("Cheveux: $hairColor")
            if (eyeColor.isNotEmpty()) appendLine("Yeux: $eyeColor")
            if (skinTone.isNotEmpty()) appendLine("Teint: $skinTone")
            if (bodyType.isNotEmpty()) appendLine("Morphologie: $bodyType")
            
            if (facialFeatures.isNotEmpty()) {
                appendLine()
                appendLine("Traits du visage: $facialFeatures")
            }
            
            if (distinctiveFeatures.isNotEmpty()) {
                appendLine()
                appendLine("Signes distinctifs: $distinctiveFeatures")
            }
        }.trim()
    }
}
