package com.narutoai.chat.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client pour la génération de vidéos courtes via Replicate API
 * Utilise des modèles de génération vidéo pour créer des clips courts
 */
class VideoGenerationClient(context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // 5 minutes pour la génération de vidéos
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val REPLICATE_BASE_URL = "https://api.replicate.com/v1"
        
        // Modèle Stable Video Diffusion (génère vidéos à partir d'image)
        private const val SVD_MODEL = "stability-ai/stable-video-diffusion-img2vid-xt"
        
        // Modèle AnimateDiff (génère animations)
        private const val ANIMATE_DIFF = "lucataco/animate-diff"
        
        private const val MAX_POLL_ATTEMPTS = 60 // Plus d'attente pour les vidéos
        private const val POLL_INTERVAL_MS = 5000L // 5 secondes
    }
    
    /**
     * Génère une vidéo courte à partir d'une image
     * @param imageUrl URL de l'image de départ
     * @param motionStrength Force du mouvement (1-255)
     * @param frameCount Nombre de frames (14 ou 25)
     * @return URL de la vidéo générée
     */
    suspend fun generateVideoFromImage(
        imageUrl: String,
        apiKey: String,
        motionStrength: Int = 127,
        frameCount: Int = 14
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Créer la prédiction
            val predictionId = createVideoPrediction(imageUrl, motionStrength, frameCount, apiKey)
                ?: return@withContext Result.failure(Exception("Échec de création de la prédiction vidéo"))
            
            // Attendre la génération
            val videoUrl = pollVideoPredictionResult(predictionId, apiKey)
                ?: return@withContext Result.failure(Exception("Timeout ou erreur de génération vidéo"))
            
            Result.success(videoUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Génère une vidéo animée à partir d'un prompt
     * @param prompt Description de l'animation
     * @param duration Durée en secondes (2-4)
     * @return URL de la vidéo générée
     */
    suspend fun generateVideoFromPrompt(
        prompt: String,
        apiKey: String,
        duration: Int = 3
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val predictionId = createAnimationPrediction(prompt, duration, apiKey)
                ?: return@withContext Result.failure(Exception("Échec de création de l'animation"))
            
            val videoUrl = pollVideoPredictionResult(predictionId, apiKey)
                ?: return@withContext Result.failure(Exception("Timeout ou erreur de génération d'animation"))
            
            Result.success(videoUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Crée une prédiction vidéo à partir d'une image
     */
    private suspend fun createVideoPrediction(
        imageUrl: String,
        motionStrength: Int,
        frameCount: Int,
        apiKey: String
    ): String? {
        try {
            val jsonBody = JSONObject().apply {
                put("version", getModelVersion(SVD_MODEL))
                put("input", JSONObject().apply {
                    put("image", imageUrl)
                    put("motion_bucket_id", motionStrength)
                    put("frames_per_second", 6)
                    put("num_frames", frameCount)
                })
            }
            
            val request = Request.Builder()
                .url("$REPLICATE_BASE_URL/predictions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                
                val responseBody = response.body?.string() ?: return null
                val jsonResponse = JSONObject(responseBody)
                return jsonResponse.getString("id")
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Crée une prédiction d'animation à partir d'un prompt
     */
    private suspend fun createAnimationPrediction(
        prompt: String,
        duration: Int,
        apiKey: String
    ): String? {
        try {
            val jsonBody = JSONObject().apply {
                put("version", getModelVersion(ANIMATE_DIFF))
                put("input", JSONObject().apply {
                    put("prompt", prompt)
                    put("num_frames", duration * 8) // ~8 fps
                    put("num_inference_steps", 25)
                    put("guidance_scale", 7.5)
                })
            }
            
            val request = Request.Builder()
                .url("$REPLICATE_BASE_URL/predictions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                
                val responseBody = response.body?.string() ?: return null
                val jsonResponse = JSONObject(responseBody)
                return jsonResponse.getString("id")
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Attend que la prédiction vidéo soit terminée
     */
    private suspend fun pollVideoPredictionResult(predictionId: String, apiKey: String): String? {
        repeat(MAX_POLL_ATTEMPTS) {
            try {
                val request = Request.Builder()
                    .url("$REPLICATE_BASE_URL/predictions/$predictionId")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    
                    val responseBody = response.body?.string() ?: return null
                    val jsonResponse = JSONObject(responseBody)
                    val status = jsonResponse.getString("status")
                    
                    when (status) {
                        "succeeded" -> {
                            val output = jsonResponse.optString("output")
                            return if (output.isNotEmpty()) output else null
                        }
                        "failed", "canceled" -> {
                            return null
                        }
                        else -> {
                            delay(POLL_INTERVAL_MS)
                        }
                    }
                }
            } catch (e: Exception) {
                delay(POLL_INTERVAL_MS)
            }
        }
        return null
    }
    
    /**
     * Récupère la version du modèle (simplifié pour l'exemple)
     */
    private fun getModelVersion(model: String): String {
        // Dans une vraie application, récupérer la version via l'API
        // Pour l'instant, retourner une version par défaut
        return when (model) {
            SVD_MODEL -> "stable-video-diffusion-img2vid-xt-1-1"
            ANIMATE_DIFF -> "latest"
            else -> "latest"
        }
    }
    
    /**
     * Génère une vidéo basée sur le contexte de la conversation
     */
    suspend fun generateVideoFromContext(
        characterName: String,
        conversationContext: String,
        groqClient: GroqClient,
        imageGenerationClient: ImageGenerationClient,
        replicateApiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Étape 1 : Générer une image du contexte
            val imageResult = imageGenerationClient.generateImageFromContext(
                characterName,
                conversationContext,
                groqClient,
                replicateApiKey
            )
            
            val imageUrl = imageResult.getOrNull()
                ?: return@withContext Result.failure(Exception("Échec de génération de l'image de base"))
            
            // Étape 2 : Animer l'image
            generateVideoFromImage(imageUrl, replicateApiKey, motionStrength = 100)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
