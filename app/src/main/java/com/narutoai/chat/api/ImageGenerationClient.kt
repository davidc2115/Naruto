package com.narutoai.chat.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client pour la génération d'images via Replicate API
 * Utilise Stable Diffusion pour créer des images basées sur des descriptions
 */
class ImageGenerationClient(context: Context) {
    
    private val apiKeyManager = ApiKeyManager(context)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // Plus long pour la génération d'images
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        // Replicate API endpoint
        private const val REPLICATE_BASE_URL = "https://api.replicate.com/v1"
        
        // Modèle Stable Diffusion XL (rapide et de bonne qualité)
        private const val SD_MODEL = "stability-ai/sdxl:39ed52f2a78e934b3ba6e2a89f5b1c712de7dfea535525255b1aa35c5565e08b"
        
        // Alternative : Flux (plus rapide)
        private const val FLUX_MODEL = "black-forest-labs/flux-schnell"
        
        private const val MAX_POLL_ATTEMPTS = 30
        private const val POLL_INTERVAL_MS = 2000L
    }
    
    /**
     * Génère une image basée sur une description
     * @param prompt La description de l'image à générer
     * @param style Style optionnel (anime, realistic, artistic, etc.)
     * @return URL de l'image générée
     */
    suspend fun generateImage(
        prompt: String,
        style: String = "realistic",
        apiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val replicateKey = apiKey ?: return@withContext Result.failure(
            Exception("Clé API Replicate requise. Obtenez-en une sur https://replicate.com")
        )
        
        try {
            // Enrichir le prompt avec le style
            val enhancedPrompt = buildPrompt(prompt, style)
            
            // Étape 1 : Créer la prédiction
            val predictionId = createPrediction(enhancedPrompt, replicateKey)
                ?: return@withContext Result.failure(Exception("Échec de création de la prédiction"))
            
            // Étape 2 : Attendre que la génération soit terminée
            val imageUrl = pollPredictionResult(predictionId, replicateKey)
                ?: return@withContext Result.failure(Exception("Timeout ou erreur de génération"))
            
            Result.success(imageUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Crée une prédiction et retourne son ID
     */
    private suspend fun createPrediction(prompt: String, apiKey: String): String? {
        try {
            val jsonBody = JSONObject().apply {
                put("version", SD_MODEL.split(":")[1])
                put("input", JSONObject().apply {
                    put("prompt", prompt)
                    put("num_outputs", 1)
                    put("aspect_ratio", "1:1")
                    put("output_format", "jpg")
                    put("output_quality", 80)
                })
            }
            
            val request = Request.Builder()
                .url("$REPLICATE_BASE_URL/predictions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val responseBody = response.body?.string() ?: return null
                val jsonResponse = JSONObject(responseBody)
                return jsonResponse.getString("id")
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Attend que la prédiction soit terminée et retourne l'URL de l'image
     */
    private suspend fun pollPredictionResult(predictionId: String, apiKey: String): String? {
        repeat(MAX_POLL_ATTEMPTS) {
            try {
                val request = Request.Builder()
                    .url("$REPLICATE_BASE_URL/predictions/$predictionId")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return null
                    }
                    
                    val responseBody = response.body?.string() ?: return null
                    val jsonResponse = JSONObject(responseBody)
                    val status = jsonResponse.getString("status")
                    
                    when (status) {
                        "succeeded" -> {
                            val output = jsonResponse.get("output")
                            return when (output) {
                                is JSONArray -> output.getString(0)
                                is String -> output
                                else -> null
                            }
                        }
                        "failed", "canceled" -> {
                            return null
                        }
                        else -> {
                            // Status: starting, processing - continuer à attendre
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
     * Construit un prompt enrichi avec le style
     */
    private fun buildPrompt(prompt: String, style: String): String {
        val styleModifiers = when (style.lowercase()) {
            "anime" -> "anime style, manga art, vibrant colors, detailed"
            "realistic" -> "photorealistic, highly detailed, 8k, professional photography"
            "artistic" -> "digital art, artistic, creative, beautiful composition"
            "cinematic" -> "cinematic lighting, movie scene, dramatic, high quality"
            "3d" -> "3d render, octane render, detailed, professional"
            else -> "high quality, detailed"
        }
        
        return "$prompt, $styleModifiers, masterpiece, best quality"
    }
    
    /**
     * Génère une image basée sur le contexte de la conversation
     * Utilise Groq pour créer un prompt d'image approprié
     */
    suspend fun generateImageFromContext(
        characterName: String,
        conversationContext: String,
        groqClient: GroqClient,
        replicateApiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Étape 1 : Demander à Groq de créer un prompt d'image
            val promptRequest = """
                Basé sur cette conversation avec $characterName:
                $conversationContext
                
                Crée un prompt court (max 50 mots) pour générer une image qui représente cette scène ou ce moment.
                Réponds UNIQUEMENT avec le prompt, sans explication.
            """.trimIndent()
            
            val promptResult = groqClient.chat(
                systemPrompt = "Tu es un expert en création de prompts pour génération d'images. Réponds uniquement avec le prompt, rien d'autre.",
                userMessage = promptRequest,
                maxTokens = 100
            )
            
            val imagePrompt = promptResult.getOrNull()
                ?: return@withContext Result.failure(Exception("Échec de création du prompt"))
            
            // Étape 2 : Générer l'image avec le prompt
            generateImage(imagePrompt, "anime", replicateApiKey)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
