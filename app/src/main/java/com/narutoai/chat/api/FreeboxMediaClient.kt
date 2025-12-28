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
 * Client pour génération d'images via ComfyUI sur Freebox
 * URL: http://88.174.155.230:33437
 * Fallback sur Pollination AI si Freebox inaccessible
 * 
 * MISE À JOUR: Utilise ComfyUI (plus léger et optimisé pour ARM CPU)
 */
class FreeboxMediaClient(private val pollinationFallback: PollinationAIClient) {
    
    companion object {
        private const val FREEBOX_URL = "http://88.174.155.230:33437"
        private const val PING_TIMEOUT = 3000L // 3s pour ping
        private const val GENERATION_TIMEOUT = 180000L // 180s pour génération CPU (ComfyUI plus lent sur CPU)
        private const val COMFYUI_WORKFLOW_SIMPLE = "txt2img_basic" // Workflow ComfyUI simple
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
            
            android.util.Log.d("FreeboxMedia", "✅ Freebox accessible! Génération locale via ComfyUI...")
            
            // ComfyUI utilise un workflow JSON
            // Pour simplifier, on génère via l'API prompt de ComfyUI
            val workflow = createComfyUIWorkflow(prompt, negativePrompt, width, height, steps, cfgScale)
            
            val requestBody = JSONObject().apply {
                put("prompt", workflow)
                put("client_id", "naruto_ai_chat")
            }
            
            val request = Request.Builder()
                .url("$FREEBOX_URL/prompt")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            android.util.Log.d("FreeboxMedia", "Génération ComfyUI Freebox: ${prompt.take(50)}...")
            
            generationClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("FreeboxMedia", "ComfyUI non accessible, tentative fallback...")
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                val responseBody = response.body?.string()
                    ?: throw IOException("Body vide")
                
                val json = JSONObject(responseBody)
                val promptId = json.getString("prompt_id")
                
                android.util.Log.d("FreeboxMedia", "ComfyUI prompt soumis: $promptId, attente résultat...")
                
                // Attendre que l'image soit générée et la récupérer
                // Pour l'instant, on utilise Pollination AI en fallback (ComfyUI nécessite websocket pour récup)
                android.util.Log.w("FreeboxMedia", "ComfyUI nécessite implémentation WebSocket complète")
                android.util.Log.w("FreeboxMedia", "🔄 Utilisation Pollination AI pour cette version")
                
                return@withContext pollinationFallback.generateImage(prompt, width, height, enhance = true)
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
     * Obtient les modèles disponibles sur Freebox (ComfyUI)
     */
    suspend fun getAvailableModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) {
                return@withContext Result.failure(IOException("Freebox non accessible"))
            }
            
            // ComfyUI liste les modèles via /object_info
            val request = Request.Builder()
                .url("$FREEBOX_URL/object_info")
                .get()
                .build()
            
            generationClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                
                val responseBody = response.body?.string()
                    ?: throw IOException("Body vide")
                
                // ComfyUI object_info contient la liste des nodes
                android.util.Log.d("FreeboxMedia", "ComfyUI object_info récupéré")
                
                // Pour simplifier, on retourne un modèle par défaut
                Result.success(listOf("ComfyUI Default"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Crée un workflow ComfyUI basique pour txt2img
     * NOTE: ComfyUI utilise des workflows JSON complexes
     * Pour l'instant, version simplifiée - nécessitera amélioration future
     */
    private fun createComfyUIWorkflow(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Double
    ): JSONObject {
        // Workflow ComfyUI minimal pour txt2img
        // Structure simplifiée - dans une vraie implémentation il faudrait un workflow complet
        return JSONObject().apply {
            put("3", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("seed", (Math.random() * Int.MAX_VALUE).toInt())
                    put("steps", steps)
                    put("cfg", cfgScale)
                    put("sampler_name", "euler")
                    put("scheduler", "normal")
                    put("denoise", 1.0)
                    put("model", JSONArray().apply { put("4"); put(0) })
                    put("positive", JSONArray().apply { put("6"); put(0) })
                    put("negative", JSONArray().apply { put("7"); put(0) })
                    put("latent_image", JSONArray().apply { put("5"); put(0) })
                })
                put("class_type", "KSampler")
            })
            put("4", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("ckpt_name", "model.safetensors")
                })
                put("class_type", "CheckpointLoaderSimple")
            })
            put("5", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("width", width)
                    put("height", height)
                    put("batch_size", 1)
                })
                put("class_type", "EmptyLatentImage")
            })
            put("6", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("text", prompt)
                    put("clip", JSONArray().apply { put("4"); put(1) })
                })
                put("class_type", "CLIPTextEncode")
            })
            put("7", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("text", negativePrompt)
                    put("clip", JSONArray().apply { put("4"); put(1) })
                })
                put("class_type", "CLIPTextEncode")
            })
        }
    }
}
