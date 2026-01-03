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
 * Utilise le modèle llama-3.2-90b-vision-preview
 */
class GroqVisionClient(private val context: Context) {
    
    companion object {
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "llama-3.2-90b-vision-preview"
        private const val MAX_IMAGE_SIZE_KB = 500 // 500KB max pour Base64
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
  "breastSize": "si femme: taille de poitrine (Petite/Moyenne/Généreuse/Très généreuse), sinon laisser vide",
  "penisSize": "si homme: taille estimée (Moyenne/Au-dessus de la moyenne/Grande/Très grande), sinon laisser vide",
  "height": "estimation taille (ex: petite ~160cm, moyenne ~170cm, grande ~180cm)",
  "facialFeatures": "traits du visage remarquables",
  "distinctiveFeatures": "signes distinctifs (tatouages, cicatrices, etc.)",
  "detailedDescription": "description physique complète en 2-3 phrases"
}

IMPORTANT: Réponds UNIQUEMENT avec le JSON, sans texte avant ou après.
                """.trimIndent()
                
                // Construire la requête JSON
                val requestJson = JSONObject().apply {
                    put("model", MODEL)
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
                
                // Créer la requête HTTP
                val requestBody = requestJson.toString()
                    .toRequestBody("application/json".toMediaType())
                
                val apiKey = getApiKey()
                if (apiKey.isEmpty()) {
                    android.util.Log.e("GroqVision", "❌ ERREUR CRITIQUE: Aucune clé API Groq trouvée dans DataStore")
                    android.util.Log.e("GroqVision", "   Allez dans Paramètres > Section 'Clés API Groq' > Bouton 'Ajouter une clé Groq'")
                    android.util.Log.e("GroqVision", "   Les clés doivent commencer par 'gsk_'")
                    return@withContext Result.failure(
                        Exception("❌ Clé API Groq non trouvée\n\nVérifiez que vous avez bien ajouté au moins une clé dans :\nParamètres > Clés API Groq > Ajouter\n\nLes clés doivent commencer par 'gsk_'")
                    )
                }
                
                val request = Request.Builder()
                    .url(GROQ_API_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                // Exécuter la requête
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    android.util.Log.e("GroqVision", "HTTP ${response.code}: $responseBody")
                    return@withContext Result.failure(
                        Exception("Erreur API: HTTP ${response.code}")
                    )
                }
                
                if (responseBody == null) {
                    return@withContext Result.failure(Exception("Réponse vide"))
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
                    breastSize = analysisJson.optString("breastSize", ""),
                    penisSize = analysisJson.optString("penisSize", ""),
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
    val breastSize: String,
    val penisSize: String,
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
