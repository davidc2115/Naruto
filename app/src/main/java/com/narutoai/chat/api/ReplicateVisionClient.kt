package com.narutoai.chat.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Client pour Replicate Vision API (LLaVA / BLIP-2)
 * GRATUIT avec clé API (pas de carte bancaire requise)
 * 
 * Modèles utilisés:
 * - yorickvp/llava-13b: Vision-Language model puissant
 * - Salesforce/blip-2: Image captioning avancé
 * 
 * Avantages:
 * ✅ GRATUIT (50 requêtes/jour sans carte bancaire)
 * ✅ Analyse COMPLÈTE et DÉTAILLÉE
 * ✅ Modèles open-source de qualité
 * ✅ Clé API simple à obtenir (10 secondes)
 * 
 * Obtenir clé: https://replicate.com/account/api-tokens
 */
class ReplicateVisionClient(private val context: Context) {
    
    companion object {
        private const val REPLICATE_API_URL = "https://api.replicate.com/v1/predictions"
        
        // Modèles vision (par ordre de préférence)
        private val VISION_MODELS = listOf(
            "yorickvp/llava-13b:b5f6212d032508382d61ff00469ddda3e32fd8a0e75dc39d8a4191bb742157fb",
            "salesforce/blip:2e1dddc8621f72155f24cf2e0adbde548458d3cab9f00c0139eea840d0ac4746"
        )
        
        private const val MAX_IMAGE_SIZE_KB = 5120 // 5MB max pour Replicate
        private const val MAX_POLL_ATTEMPTS = 30
        private const val POLL_INTERVAL_MS = 2000L // 2 secondes
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Analyse une image et génère un descriptif physique COMPLET
     */
    suspend fun analyzePhotoForCharacter(imageUri: Uri): Result<PhysicalDescription> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("ReplicateVision", "🎨 Démarrage analyse avec Replicate")
                
                // Charger et encoder l'image en Base64 data URI
                val base64Image = loadAndCompressImageToDataUri(imageUri)
                    ?: return@withContext Result.failure(Exception("Impossible de charger l'image"))
                
                android.util.Log.d("ReplicateVision", "📷 Image encodée")
                
                // Obtenir clé API
                val apiKey = getApiKey()
                if (apiKey.isEmpty()) {
                    return@withContext Result.failure(
                        Exception("❌ Clé API Replicate manquante\n\nObtenez une clé GRATUITE (10 secondes) sur:\nhttps://replicate.com/account/api-tokens\n\nAjoutez-la dans les Paramètres de l'app.")
                    )
                }
                
                // Essayer chaque modèle avec fallback
                var lastException: Exception? = null
                
                for ((index, model) in VISION_MODELS.withIndex()) {
                    try {
                        android.util.Log.d("ReplicateVision", "🔄 Tentative modèle ${index + 1}/${VISION_MODELS.size}")
                        
                        val description = analyzeWithModel(model, base64Image, apiKey)
                        
                        if (description != null) {
                            android.util.Log.d("ReplicateVision", "✅ Analyse réussie")
                            return@withContext Result.success(description)
                        }
                        
                    } catch (e: Exception) {
                        android.util.Log.w("ReplicateVision", "⚠️ Échec modèle ${index + 1}: ${e.message}")
                        lastException = e
                    }
                }
                
                // Tous les modèles ont échoué
                Result.failure(
                    lastException ?: Exception("Tous les modèles ont échoué")
                )
                
            } catch (e: Exception) {
                android.util.Log.e("ReplicateVision", "❌ Erreur: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Analyse avec un modèle spécifique
     */
    private suspend fun analyzeWithModel(
        modelVersion: String,
        imageDataUri: String,
        apiKey: String
    ): PhysicalDescription? {
        return withContext(Dispatchers.IO) {
            try {
                // Prompt détaillé pour obtenir description physique complète
                val prompt = """
Analyze this photo in detail and provide a comprehensive physical description in French.

Describe in JSON format:
{
  "age": "estimated age or range (e.g., 18-25 ans, young adult, mature)",
  "gender": "homme/femme",
  "hairColor": "hair color and style (e.g., blonds longs, noirs courts, châtains mi-longs)",
  "eyeColor": "eye color (bleus, marron, verts, noisette, gris)",
  "skinTone": "skin tone (clair, mat, bronzé, foncé, olive)",
  "bodyType": "body type (mince, athlétique, musclé, voluptueux, corpulent, moyen)",
  "height": "estimated height (petite ~155-165cm, moyenne ~165-175cm, grande ~175-185cm)",
  "facialFeatures": "notable facial features (souriant, traits fins, expression sérieuse, etc.)",
  "distinctiveFeatures": "distinctive marks (tatouages, cicatrices, lunettes, barbe, piercings, etc.)",
  "detailedDescription": "complete physical description in 3-4 sentences in French"
}

IMPORTANT: Respond ONLY with the JSON, nothing else.
                """.trimIndent()
                
                // Créer la prédiction
                val predictionId = createPrediction(modelVersion, imageDataUri, prompt, apiKey)
                    ?: throw Exception("Échec création prédiction")
                
                // Attendre le résultat
                val resultText = pollPredictionResult(predictionId, apiKey)
                    ?: throw Exception("Timeout ou erreur")
                
                android.util.Log.d("ReplicateVision", "📝 Résultat: ${resultText.take(200)}...")
                
                // Parser le résultat
                parseVisionResponse(resultText)
                
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    /**
     * Crée une prédiction Replicate
     */
    private suspend fun createPrediction(
        modelVersion: String,
        imageDataUri: String,
        prompt: String,
        apiKey: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("version", modelVersion)
                    put("input", JSONObject().apply {
                        put("image", imageDataUri)
                        put("prompt", prompt)
                        put("max_tokens", 1024)
                        put("temperature", 0.2) // Basse température pour précision
                    })
                }
                
                val request = Request.Builder()
                    .url(REPLICATE_API_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.e("ReplicateVision", "❌ HTTP ${response.code}: ${response.body?.string()}")
                        return@withContext null
                    }
                    
                    val responseBody = response.body?.string()
                    val jsonResponse = JSONObject(responseBody ?: "{}")
                    jsonResponse.getString("id")
                }
            } catch (e: Exception) {
                android.util.Log.e("ReplicateVision", "Erreur création: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Attend que la prédiction soit terminée
     */
    private suspend fun pollPredictionResult(predictionId: String, apiKey: String): String? {
        repeat(MAX_POLL_ATTEMPTS) { attempt ->
            try {
                delay(POLL_INTERVAL_MS)
                
                val request = Request.Builder()
                    .url("$REPLICATE_API_URL/$predictionId")
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@repeat
                    
                    val responseBody = response.body?.string() ?: return@repeat
                    val jsonResponse = JSONObject(responseBody)
                    val status = jsonResponse.getString("status")
                    
                    android.util.Log.d("ReplicateVision", "⏳ Status: $status (tentative ${attempt + 1}/$MAX_POLL_ATTEMPTS)")
                    
                    when (status) {
                        "succeeded" -> {
                            val output = jsonResponse.optJSONArray("output")
                            if (output != null && output.length() > 0) {
                                // Concaténer toutes les parties de sortie
                                val fullText = StringBuilder()
                                for (i in 0 until output.length()) {
                                    fullText.append(output.getString(i))
                                }
                                return fullText.toString()
                            }
                            return jsonResponse.optString("output", "")
                        }
                        "failed", "canceled" -> {
                            val error = jsonResponse.optString("error", "Erreur inconnue")
                            android.util.Log.e("ReplicateVision", "❌ Prédiction échouée: $error")
                            return null
                        }
                        else -> {
                            // En cours, continuer à attendre
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ReplicateVision", "Erreur poll ${attempt + 1}: ${e.message}")
            }
        }
        
        android.util.Log.e("ReplicateVision", "❌ Timeout après $MAX_POLL_ATTEMPTS tentatives")
        return null
    }
    
    /**
     * Parse la réponse vision
     */
    private fun parseVisionResponse(text: String): PhysicalDescription? {
        try {
            // Extraire le JSON de la réponse
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}')
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonText = text.substring(jsonStart, jsonEnd + 1)
                val json = JSONObject(jsonText)
                
                return PhysicalDescription(
                    age = json.optString("age", ""),
                    gender = json.optString("gender", ""),
                    hairColor = json.optString("hairColor", ""),
                    eyeColor = json.optString("eyeColor", ""),
                    skinTone = json.optString("skinTone", ""),
                    bodyType = json.optString("bodyType", ""),
                    height = json.optString("height", ""),
                    facialFeatures = json.optString("facialFeatures", ""),
                    distinctiveFeatures = json.optString("distinctiveFeatures", ""),
                    detailedDescription = json.optString("detailedDescription", "")
                )
            }
            
            // Si pas de JSON valide, créer une description à partir du texte
            return PhysicalDescription(
                age = "",
                gender = "",
                hairColor = "",
                eyeColor = "",
                skinTone = "",
                bodyType = "",
                height = "",
                facialFeatures = "",
                distinctiveFeatures = "",
                detailedDescription = text.take(500)
            )
            
        } catch (e: Exception) {
            android.util.Log.e("ReplicateVision", "Erreur parsing: ${e.message}")
            return null
        }
    }
    
    /**
     * Obtient la clé API Replicate
     */
    private fun getApiKey(): String {
        try {
            val prefs = context.getSharedPreferences("naruto_ai_prefs", Context.MODE_PRIVATE)
            val savedKey = prefs.getString("replicate_api_key", "") ?: ""
            
            if (savedKey.isNotEmpty()) {
                android.util.Log.d("ReplicateVision", "🔑 Clé Replicate trouvée")
                return savedKey
            }
            
            android.util.Log.e("ReplicateVision", "❌ Aucune clé API trouvée")
            return ""
            
        } catch (e: Exception) {
            android.util.Log.e("ReplicateVision", "❌ Erreur chargement clé: ${e.message}")
            return ""
        }
    }
    
    /**
     * Charge et compresse une image en Data URI
     */
    private fun loadAndCompressImageToDataUri(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            // Redimensionner si trop grande
            val maxDimension = 2048
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
            var quality = 90
            
            do {
                outputStream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                quality -= 10
            } while (outputStream.size() > MAX_IMAGE_SIZE_KB * 1024 && quality > 30)
            
            val imageBytes = outputStream.toByteArray()
            outputStream.close()
            
            android.util.Log.d("ReplicateVision", "📦 Image compressée: ${imageBytes.size / 1024}KB")
            
            // Créer data URI
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
            
        } catch (e: Exception) {
            android.util.Log.e("ReplicateVision", "❌ Erreur compression: ${e.message}", e)
            null
        }
    }
}
