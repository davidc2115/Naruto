package com.narutoai.chat.api

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
 * Client pour Stable Horde - Alternative GRATUITE à Pollination AI
 * https://stablehorde.net
 * 
 * Avantages:
 * - 100% Gratuit, sans limites
 * - Pas de clé API nécessaire (anonymous key: 0000000000)
 * - Support NSFW complet
 * - Réseau décentralisé (toujours disponible)
 * - Haute qualité (Stable Diffusion 1.5, SDXL)
 */
class StableHordeClient {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS) // Temps d'attente generation
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    companion object {
        private const val BASE_URL = "https://stablehorde.net/api/v2"
        private const val ANONYMOUS_KEY = "0000000000" // Clé anonyme gratuite
        private const val MAX_WAIT_TIME = 180000L // 3 minutes max
        private const val POLL_INTERVAL = 5000L // Vérifier toutes les 5 secondes
    }
    
    /**
     * Génère une image via Stable Horde
     * @param prompt Description de l'image
     * @param width Largeur
     * @param height Hauteur
     * @param nsfw Si true, autorise NSFW
     * @return Base64 de l'image ou URL
     */
    suspend fun generateImage(
        prompt: String,
        negativePrompt: String = "low quality, blurry, distorted, ugly, deformed",
        width: Int = 512,
        height: Int = 768,
        steps: Int = 20,
        cfgScale: Double = 7.0,
        nsfw: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("StableHorde", "🎨 Génération via Stable Horde...")
            android.util.Log.d("StableHorde", "📝 Prompt: ${prompt.take(50)}...")
            
            // 1. Soumettre la requête de génération
            val requestId = submitGenerationRequest(prompt, negativePrompt, width, height, steps, cfgScale, nsfw)
            android.util.Log.d("StableHorde", "✅ Requête soumise: $requestId")
            
            // 2. Attendre et récupérer l'image
            val imageBase64 = waitForGeneration(requestId)
            
            android.util.Log.d("StableHorde", "✅ Image générée! (${imageBase64.length / 1024}KB)")
            Result.success("data:image/png;base64,$imageBase64")
            
        } catch (e: Exception) {
            android.util.Log.e("StableHorde", "❌ Erreur: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Soumet une requête de génération et retourne l'ID
     */
    private suspend fun submitGenerationRequest(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Double,
        nsfw: Boolean
    ): String = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("prompt", prompt)
            put("params", JSONObject().apply {
                put("width", width)
                put("height", height)
                put("steps", steps)
                put("cfg_scale", cfgScale)
                put("sampler_name", "k_euler")
                put("seed", "")
                put("n", 1) // 1 image
                // Negative prompt via params
                if (negativePrompt.isNotEmpty()) {
                    put("negative_prompt", negativePrompt)
                }
            })
            put("nsfw", nsfw) // Important: autoriser NSFW
            put("trusted_workers", false) // Accepter tous les workers
            put("slow_workers", true) // Accepter workers lents (plus de disponibilité)
            put("censor_nsfw", false) // NE PAS censurer NSFW
            put("models", JSONArray().apply {
                add("stable_diffusion") // Modèle par défaut
            })
            put("r2", true) // Retourner image en base64
        }
        
        val request = Request.Builder()
            .url("$BASE_URL/generate/async")
            .addHeader("Content-Type", "application/json")
            .addHeader("apikey", ANONYMOUS_KEY) // Clé anonyme
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("Body vide")
            val json = JSONObject(responseBody)
            
            json.getString("id")
        }
    }
    
    /**
     * Attend que la génération soit terminée et retourne l'image
     */
    private suspend fun waitForGeneration(requestId: String): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var lastPosition = -1
        
        while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME) {
            // Vérifier le status
            val request = Request.Builder()
                .url("$BASE_URL/generate/check/$requestId")
                .addHeader("apikey", ANONYMOUS_KEY)
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Check failed: HTTP ${response.code}")
                }
                
                val json = JSONObject(response.body?.string() ?: throw IOException("Body vide"))
                
                val done = json.optBoolean("done", false)
                val faulted = json.optBoolean("faulted", false)
                val queuePosition = json.optInt("queue_position", 0)
                val waitTime = json.optInt("wait_time", 0)
                
                // Log progression
                if (queuePosition != lastPosition) {
                    android.util.Log.d("StableHorde", "⏳ Position queue: $queuePosition, Attente: ${waitTime}s")
                    lastPosition = queuePosition
                }
                
                if (faulted) {
                    throw IOException("Génération échouée (faulted)")
                }
                
                if (done) {
                    // Récupérer l'image
                    return@withContext fetchGeneratedImage(requestId)
                }
            }
            
            // Attendre avant de re-vérifier
            delay(POLL_INTERVAL)
        }
        
        throw IOException("Timeout: Génération trop longue (>${MAX_WAIT_TIME / 1000}s)")
    }
    
    /**
     * Récupère l'image générée
     */
    private suspend fun fetchGeneratedImage(requestId: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/generate/status/$requestId")
            .addHeader("apikey", ANONYMOUS_KEY)
            .get()
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Status failed: HTTP ${response.code}")
            }
            
            val json = JSONObject(response.body?.string() ?: throw IOException("Body vide"))
            
            val generations = json.optJSONArray("generations")
            if (generations == null || generations.length() == 0) {
                throw IOException("Aucune image générée")
            }
            
            val firstGen = generations.getJSONObject(0)
            
            // Stable Horde retourne soit:
            // - "img": base64 string (si r2=true)
            // - "url": URL de l'image
            if (firstGen.has("img")) {
                firstGen.getString("img")
            } else if (firstGen.has("url")) {
                // Télécharger l'image depuis l'URL
                val imageUrl = firstGen.getString("url")
                downloadImageAsBase64(imageUrl)
            } else {
                throw IOException("Format de réponse inconnu")
            }
        }
    }
    
    /**
     * Télécharge une image depuis URL et la convertit en Base64
     */
    private suspend fun downloadImageAsBase64(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed: HTTP ${response.code}")
            }
            
            val bytes = response.body?.bytes() ?: throw IOException("Image vide")
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }
    
    /**
     * Teste si Stable Horde est accessible
     */
    suspend fun ping(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/status/heartbeat")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
