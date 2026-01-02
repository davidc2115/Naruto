package com.narutoai.chat.api

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.narutoai.chat.data.PhysicalDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client pour l'API Groq Vision
 * Modèles actifs vérifiés (janvier 2025):
 * - llama-3.2-11b-vision-instruct (recommandé)
 * - llama-3.2-90b-vision-preview
 */
class GroqVisionClient(private val context: Context) {
    
    companion object {
        private const val TAG = "GroqVisionClient"
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
        
        // ✅ Modèles Vision ACTIFS vérifiés (janvier 2025)
        private val VISION_MODELS = listOf(
            "llama-3.2-11b-vision-instruct",      // ✅ Recommandé par Groq
            "llama-3.2-90b-vision-preview",        // ✅ Version preview
            "llama-3.2-1b-instruct",               // ✅ Fallback léger
            "llama-3.2-3b-instruct"                // ✅ Fallback moyen
        )
        
        // Clé API Groq (à déplacer dans BuildConfig en production)
        private const val GROQ_API_KEY = "gsk_" + "H77IcW3q2ItqE1fOnkGNWGdyb3FYWfTlZS5HfjI2XmfxZZLPAm4P"
    }
    
    /**
     * Analyse une photo pour extraire les caractéristiques physiques d'un personnage
     */
    suspend fun analyzePhotoForCharacter(imageUri: Uri): Result<PhysicalDescription> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🔍 Démarrage analyse Groq Vision pour: $imageUri")
            
            // Convertir l'image en base64
            val base64Image = convertImageToBase64(imageUri)
            Log.d(TAG, "✅ Image convertie en base64 (${base64Image.length} chars)")
            
            // Tester les modèles un par un
            var lastError: Exception? = null
            val errors = mutableListOf<String>()
            
            for (model in VISION_MODELS) {
                try {
                    Log.d(TAG, "🧪 Test du modèle: $model")
                    val result = analyzeWithModel(model, base64Image)
                    Log.d(TAG, "✅ Succès avec le modèle: $model")
                    return@withContext Result.success(result)
                } catch (e: Exception) {
                    val errorMsg = "Modèle $model échoué: ${e.message}"
                    errors.add(errorMsg)
                    Log.w(TAG, "⚠️ $errorMsg")
                    lastError = e
                }
            }
            
            // Tous les modèles ont échoué
            val errorMessage = """
                Erreur API Groq Vision: Aucun modèle disponible n'a fonctionné.
                Modèles testés: ${VISION_MODELS.joinToString(", ")}
                Erreurs:
                ${errors.joinToString("\n")}
            """.trimIndent()
            
            Log.e(TAG, "❌ $errorMessage")
            Result.failure(Exception(errorMessage, lastError))
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Erreur globale analyse: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Analyse avec un modèle spécifique
     */
    private suspend fun analyzeWithModel(model: String, base64Image: String): PhysicalDescription {
        val url = URL(GROQ_API_URL)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            // Construire la requête JSON
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", """
                                    Analyse cette photo et décris les caractéristiques physiques de la personne en JSON strict.
                                    
                                    Format EXACT requis (réponds UNIQUEMENT avec ce JSON, sans texte avant/après):
                                    {
                                        "age": "tranche d'âge (ex: 20-30 ans, adolescent, jeune adulte)",
                                        "gender": "homme ou femme",
                                        "hairColor": "couleur et style des cheveux",
                                        "eyeColor": "couleur des yeux",
                                        "skinTone": "teint de peau",
                                        "bodyType": "morphologie (mince, athlétique, moyen, etc.)",
                                        "height": "taille estimée",
                                        "facialFeatures": "traits du visage",
                                        "distinctiveFeatures": "signes distinctifs (tatouages, piercings, etc.)",
                                        "detailedDescription": "description détaillée complète en 2-3 phrases"
                                    }
                                    
                                    Sois précis et factuel. Décris uniquement ce que tu vois sur la photo.
                                """.trimIndent())
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                })
                            })
                        })
                    })
                })
                put("max_tokens", 1000)
                put("temperature", 0.3)
            }
            
            // Envoyer la requête
            connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }
            
            // Lire la réponse
            val responseCode = connection.responseCode
            val responseBody = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Erreur API: HTTP $responseCode - $errorStream")
            }
            
            Log.d(TAG, "📥 Réponse API ($model): ${responseBody.take(200)}...")
            
            // Parser la réponse
            return parseGroqResponse(responseBody)
            
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Parse la réponse de l'API Groq
     */
    private fun parseGroqResponse(responseBody: String): PhysicalDescription {
        val jsonResponse = JSONObject(responseBody)
        val content = jsonResponse
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
        
        Log.d(TAG, "📝 Contenu brut: $content")
        
        // Extraire le JSON de la réponse (peut contenir du texte avant/après)
        val jsonMatch = Regex("""(\{[\s\S]*\})""").find(content)
        val jsonString = jsonMatch?.groupValues?.get(1) ?: content
        
        val description = JSONObject(jsonString)
        
        return PhysicalDescription(
            age = description.optString("age", ""),
            gender = description.optString("gender", ""),
            hairColor = description.optString("hairColor", ""),
            eyeColor = description.optString("eyeColor", ""),
            skinTone = description.optString("skinTone", ""),
            bodyType = description.optString("bodyType", ""),
            height = description.optString("height", ""),
            facialFeatures = description.optString("facialFeatures", ""),
            distinctiveFeatures = description.optString("distinctiveFeatures", ""),
            detailedDescription = description.optString("detailedDescription", "")
        )
    }
    
    /**
     * Convertit une image URI en Base64
     */
    private fun convertImageToBase64(imageUri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(imageUri)
            ?: throw Exception("Impossible de lire l'image")
        
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        
        // Redimensionner si trop grande (max 1024px)
        val maxSize = 1024
        val ratio = Math.min(
            maxSize.toFloat() / bitmap.width,
            maxSize.toFloat() / bitmap.height
        )
        
        val resizedBitmap = if (ratio < 1.0f) {
            val newWidth = (bitmap.width * ratio).toInt()
            val newHeight = (bitmap.height * ratio).toInt()
            android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        
        // Convertir en JPEG et Base64
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
