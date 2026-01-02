package com.narutoai.chat.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
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
 * Client pour Google Gemini Vision API (analyse d'images)
 * GRATUIT avec quota généreux : 60 requêtes/minute, 1500/jour
 * https://ai.google.dev/gemini-api/docs/vision
 */
class GeminiVisionClient(private val context: Context) {
    
    companion object {
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        // Modèle gratuit avec vision
        private const val MODEL = "gemini-1.5-flash-latest" // Ou "gemini-pro-vision" 
        private const val MAX_IMAGE_SIZE_KB = 4096 // 4MB max pour Gemini
        
        // Clé API Google Gemini (publique pour démo, obtenez la vôtre sur https://makersuite.google.com/app/apikey)
        // Pour production, stockez-la en sécurité
        private const val DEFAULT_API_KEY = "REMPLACER_PAR_VOTRE_CLE" // L'utilisateur devra ajouter sa clé
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Analyse une image et génère un descriptif physique détaillé
     * GRATUIT et ILLIMITÉ (dans les limites du quota Google)
     */
    suspend fun analyzePhotoForCharacter(imageUri: Uri): Result<PhysicalDescription> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("GeminiVision", "🎨 Démarrage analyse avec Gemini Vision")
                
                // Charger et compresser l'image
                val base64Image = loadAndCompressImage(imageUri)
                    ?: return@withContext Result.failure(Exception("Impossible de charger l'image"))
                
                android.util.Log.d("GeminiVision", "📷 Image encodée (${base64Image.length} chars)")
                
                // Obtenir clé API
                val apiKey = getApiKey()
                if (apiKey.isEmpty()) {
                    return@withContext Result.failure(
                        Exception("❌ Clé API Google Gemini manquante\n\nObtenez une clé gratuite sur:\nhttps://makersuite.google.com/app/apikey\n\nAjoutez-la dans les Paramètres de l'app.")
                    )
                }
                
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

IMPORTANT: Réponds UNIQUEMENT avec le JSON, sans texte avant ou après.
                """.trimIndent()
                
                // Construire la requête Gemini
                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                // Texte prompt
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                                // Image en base64
                                put(JSONObject().apply {
                                    put("inline_data", JSONObject().apply {
                                        put("mime_type", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                            })
                        })
                    })
                    // Configuration de génération
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.4)
                        put("topK", 32)
                        put("topP", 1)
                        put("maxOutputTokens", 1024)
                    })
                }
                
                // URL avec clé API
                val url = "$GEMINI_API_URL/$MODEL:generateContent?key=$apiKey"
                
                android.util.Log.d("GeminiVision", "🚀 Envoi requête à Gemini...")
                
                // Créer la requête HTTP
                val requestBody = requestJson.toString()
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                // Exécuter la requête
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    android.util.Log.e("GeminiVision", "❌ HTTP ${response.code}: $responseBody")
                    
                    // Parser l'erreur
                    try {
                        val errorJson = JSONObject(responseBody ?: "{}")
                        val errorMsg = errorJson.optJSONObject("error")?.optString("message") ?: "Erreur inconnue"
                        return@withContext Result.failure(
                            Exception("Erreur Google Gemini: HTTP ${response.code}\n$errorMsg")
                        )
                    } catch (e: Exception) {
                        return@withContext Result.failure(
                            Exception("Erreur API: HTTP ${response.code}")
                        )
                    }
                }
                
                if (responseBody == null) {
                    return@withContext Result.failure(Exception("Réponse vide"))
                }
                
                android.util.Log.d("GeminiVision", "✅ Réponse reçue")
                
                // Parser la réponse Gemini
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.getJSONArray("candidates")
                
                if (candidates.length() == 0) {
                    return@withContext Result.failure(Exception("Aucune réponse générée"))
                }
                
                val content = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
                
                android.util.Log.d("GeminiVision", "📝 Réponse brute: ${content.take(200)}...")
                
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
                
                android.util.Log.d("GeminiVision", "🎉 Analyse réussie!")
                Result.success(description)
                
            } catch (e: Exception) {
                android.util.Log.e("GeminiVision", "❌ Erreur analyse: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Obtient la clé API Google Gemini
     * 1. Cherche dans les SharedPreferences
     * 2. Sinon utilise la clé par défaut (à remplacer)
     */
    private fun getApiKey(): String {
        try {
            // Chercher dans SharedPreferences
            val prefs = context.getSharedPreferences("naruto_ai_prefs", Context.MODE_PRIVATE)
            val savedKey = prefs.getString("gemini_api_key", "") ?: ""
            
            if (savedKey.isNotEmpty()) {
                android.util.Log.d("GeminiVision", "🔑 Clé Gemini trouvée dans prefs")
                return savedKey
            }
            
            // Fallback sur clé par défaut
            if (DEFAULT_API_KEY != "REMPLACER_PAR_VOTRE_CLE") {
                android.util.Log.d("GeminiVision", "🔑 Utilisation clé par défaut")
                return DEFAULT_API_KEY
            }
            
            android.util.Log.e("GeminiVision", "❌ Aucune clé API trouvée")
            return ""
            
        } catch (e: Exception) {
            android.util.Log.e("GeminiVision", "❌ Erreur chargement clé: ${e.message}")
            return ""
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
            
            // Redimensionner si trop grande (Gemini accepte jusqu'à 4MB)
            val maxDimension = 2048 // Plus grand que Groq car Gemini est plus permissif
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
            var quality = 90 // Qualité plus haute car Gemini accepte plus gros
            
            do {
                outputStream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                quality -= 10
            } while (outputStream.size() > MAX_IMAGE_SIZE_KB * 1024 && quality > 30)
            
            val imageBytes = outputStream.toByteArray()
            outputStream.close()
            
            android.util.Log.d("GeminiVision", "📦 Image compressée: ${imageBytes.size / 1024}KB, qualité: $quality")
            
            Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            
        } catch (e: Exception) {
            android.util.Log.e("GeminiVision", "❌ Erreur compression: ${e.message}", e)
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
 * Résultat de l'analyse physique (même structure que Groq)
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
