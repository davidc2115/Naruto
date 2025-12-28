package com.narutoai.chat.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client pour génération d'images via Stable Diffusion WebUI sur Freebox
 * URL: http://88.174.155.230:7860
 * Fallback sur Pollination AI si Freebox inaccessible
 */
class FreeboxMediaClient(private val pollinationFallback: PollinationAIClient) {
    
    companion object {
        private const val FREEBOX_URL = "http://88.174.155.230:33437"
        private const val PING_TIMEOUT = 3000L // 3s pour ping
        private const val GENERATION_TIMEOUT = 120000L // 120s pour génération (augmenté pour CPU)
    }
    
    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(PING_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(PING_TIMEOUT, TimeUnit.MILLISECONDS)
        .writeTimeout(PING_TIMEOUT, TimeUnit.MILLISECONDS)
        .build()
    
    private val generationClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(GENERATION_TIMEOUT, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Vérifie si la Freebox est accessible (ping rapide)
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(FREEBOX_URL)
                .head()
                .build()
            
            pingClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Génère une image via Stable Diffusion WebUI sur Freebox
     * PRIORITÉ 1: Freebox SD WebUI (local, illimité, sans censure)
     * PRIORITÉ 2 (Fallback): Pollination AI (si Freebox inaccessible)
     */
    suspend fun generateImage(
        prompt: String,
        negativePrompt: String = "low quality, blurry, distorted, ugly, deformed",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Double = 7.0,
        isNSFW: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // PRIORITÉ 1: Essayer Freebox en premier
            android.util.Log.d("FreeboxMedia", "🎯 PRIORITÉ 1: Tentative génération via Freebox SD WebUI...")
            
            // Vérifier disponibilité Freebox
            if (!isAvailable()) {
                android.util.Log.w("FreeboxMedia", "⚠️ Freebox non accessible (timeout 3s)")
                android.util.Log.w("FreeboxMedia", "🔄 FALLBACK: Utilisation Pollination AI")
                return@withContext pollinationFallback.generateImage(prompt, width, height, enhance = true)
            }
            
            android.util.Log.d("FreeboxMedia", "✅ Freebox accessible! Génération locale en cours...")
            
            // Construire requête Stable Diffusion
            val requestBody = JSONObject().apply {
                put("prompt", prompt)
                put("negative_prompt", negativePrompt)
                put("width", width)
                put("height", height)
                put("steps", steps)
                put("cfg_scale", cfgScale)
                put("sampler_name", "Euler a")
                
                // Paramètres NSFW
                if (isNSFW) {
                    put("enable_hr", false) // Pas de upscaling pour NSFW (plus rapide)
                    put("denoising_strength", 0.7)
                }
            }
            
            val request = Request.Builder()
                .url("$FREEBOX_URL/sdapi/v1/txt2img")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            android.util.Log.d("FreeboxMedia", "Génération Freebox: ${prompt.take(50)}...")
            
            generationClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                val responseBody = response.body?.string()
                    ?: throw IOException("Body vide")
                
                val json = JSONObject(responseBody)
                val imagesArray = json.getJSONArray("images")
                
                if (imagesArray.length() == 0) {
                    throw IOException("Aucune image générée")
                }
                
                // Extraire base64 de la première image
                val base64Image = imagesArray.getString(0)
                
                // Retourner data URL (Freebox source)
                val imageUrl = "data:image/png;base64,$base64Image"
                
                android.util.Log.d("FreeboxMedia", "✅ Image générée via FREEBOX (${base64Image.length / 1024}KB)")
                android.util.Log.d("FreeboxMedia", "📍 Source: Freebox Stable Diffusion (local)")
                Result.success(imageUrl)
            }
            
        } catch (e: Exception) {
            android.util.Log.e("FreeboxMedia", "❌ Erreur Freebox: ${e.message}")
            android.util.Log.w("FreeboxMedia", "🔄 FALLBACK: Utilisation Pollination AI")
            // FALLBACK automatique sur Pollination AI
            pollinationFallback.generateImage(prompt, width, height, enhance = true)
        }
    }
    
    /**
     * Génère une "vidéo" (GIF animé) via img2img sur Freebox
     * PRIORITÉ 1: Freebox SD WebUI (local, illimité, sans censure)
     * PRIORITÉ 2 (Fallback): Pollination AI
     */
    suspend fun generateVideo(
        prompt: String,
        negativePrompt: String = "low quality, blurry, distorted, ugly, deformed",
        width: Int = 512,
        height: Int = 512,
        isNSFW: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // PRIORITÉ 1: Essayer Freebox en premier
            android.util.Log.d("FreeboxMedia", "🎯 PRIORITÉ 1: Tentative génération vidéo via Freebox...")
            
            // Vérifier disponibilité
            if (!isAvailable()) {
                android.util.Log.w("FreeboxMedia", "⚠️ Freebox non accessible")
                android.util.Log.w("FreeboxMedia", "🔄 FALLBACK: Utilisation Pollination AI pour GIF")
                // Pollination AI génère GIF via paramètre &nologo=true&motion=true
                return@withContext pollinationFallback.generateImage(
                    prompt = "$prompt, animated gif, motion blur, dynamic",
                    width = width,
                    height = height,
                    enhance = true
                )
            }
            
            android.util.Log.d("FreeboxMedia", "✅ Freebox accessible! Génération vidéo locale...")
            
            // Pour l'instant, générer une image simple
            // TODO: Implémenter vraie génération vidéo/GIF avec AnimateDiff
            generateImage(prompt, negativePrompt, width, height, isNSFW = isNSFW)
            
        } catch (e: Exception) {
            android.util.Log.e("FreeboxMedia", "❌ Erreur vidéo Freebox: ${e.message}")
            android.util.Log.w("FreeboxMedia", "🔄 FALLBACK: Utilisation Pollination AI")
            pollinationFallback.generateImage(
                prompt = "$prompt, animated style",
                width = width,
                height = height,
                enhance = true
            )
        }
    }
    
    /**
     * Obtient les modèles disponibles sur Freebox
     */
    suspend fun getAvailableModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) {
                return@withContext Result.failure(IOException("Freebox non accessible"))
            }
            
            val request = Request.Builder()
                .url("$FREEBOX_URL/sdapi/v1/sd-models")
                .get()
                .build()
            
            generationClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                
                val responseBody = response.body?.string()
                    ?: throw IOException("Body vide")
                
                val jsonArray = JSONArray(responseBody)
                val models = mutableListOf<String>()
                
                for (i in 0 until jsonArray.length()) {
                    val model = jsonArray.getJSONObject(i)
                    models.add(model.getString("model_name"))
                }
                
                Result.success(models)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
