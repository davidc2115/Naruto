package com.narutoai.chat.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Client ComfyUI avec support WebSocket
 * Pour génération d'images via Freebox
 * 
 * API: http://88.174.155.230:33437
 * WebSocket: ws://88.174.155.230:33437/ws
 */
class ComfyUIClient {
    
    companion object {
        private const val TAG = "ComfyUIClient"
        private const val COMFY_URL = "http://88.174.155.230:33437"
        private const val COMFY_WS_URL = "ws://88.174.155.230:33437/ws"
        
        private const val PING_TIMEOUT = 15000L // Augmenté de 3s à 15s
        private const val GENERATION_TIMEOUT = 600000L // 10 min au lieu de 3 min
        
        // Paramètres optimisés pour ARM CPU (ULTRA-RAPIDE)
        private const val FAST_STEPS = 8 // Réduit de 12 à 8 pour vitesse
        private const val FAST_WIDTH = 512
        private const val FAST_HEIGHT = 512
        private const val FAST_CFG = 5.0 // Réduit de 6.0 à 5.0 pour vitesse
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(GENERATION_TIMEOUT, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(PING_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(PING_TIMEOUT, TimeUnit.MILLISECONDS)
        .build()
    
    /**
     * Vérifie si ComfyUI est accessible
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(COMFY_URL)
                .head()
                .build()
            
            pingClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "ComfyUI non accessible: ${e.message}")
            false
        }
    }
    
    /**
     * Génère une image via ComfyUI (optimisé pour ARM CPU)
     */
    suspend fun generateImage(
        prompt: String,
        negativePrompt: String = "low quality, blurry, distorted",
        width: Int = FAST_WIDTH,
        height: Int = FAST_HEIGHT,
        steps: Int = FAST_STEPS, // Réduit pour vitesse
        cfgScale: Double = FAST_CFG // Réduit pour vitesse
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) {
                return@withContext Result.failure(IOException("ComfyUI non accessible"))
            }
            
            Log.d(TAG, "Génération image via ComfyUI: ${prompt.take(50)}...")
            
            // Créer un client ID unique
            val clientId = UUID.randomUUID().toString()
            
            // Créer le workflow
            val workflow = createWorkflow(prompt, negativePrompt, width, height, steps, cfgScale)
            
            // Soumettre le prompt
            val promptId = submitPrompt(workflow, clientId)
            Log.d(TAG, "Prompt soumis: $promptId")
            
            // Attendre le résultat via WebSocket
            val imageData = waitForCompletion(promptId, clientId)
            
            Log.d(TAG, "✅ Image générée via ComfyUI (${imageData.length / 1024}KB)")
            Result.success("data:image/png;base64,$imageData")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ComfyUI: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Soumet un workflow à ComfyUI
     */
    private suspend fun submitPrompt(workflow: JSONObject, clientId: String): String = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("prompt", workflow)
            put("client_id", clientId)
        }
        
        val request = Request.Builder()
            .url("$COMFY_URL/prompt")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            
            val json = JSONObject(response.body?.string() ?: throw IOException("Body vide"))
            json.getString("prompt_id")
        }
    }
    
    /**
     * Attend la complétion via WebSocket et récupère l'image
     */
    private suspend fun waitForCompletion(promptId: String, clientId: String): String = 
        withTimeout(GENERATION_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                val wsUrl = "$COMFY_WS_URL?clientId=$clientId"
                val request = Request.Builder().url(wsUrl).build()
                
                val webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val message = JSONObject(text)
                            val type = message.optString("type")
                            
                            when (type) {
                                "executing" -> {
                                    val data = message.optJSONObject("data")
                                    val node = data?.optString("node")
                                    if (node == null) {
                                        // Exécution terminée, récupérer l'image
                                        try {
                                            val imageData = fetchGeneratedImage(promptId)
                                            if (continuation.isActive) {
                                                continuation.resume(imageData)
                                            }
                                            webSocket.close(1000, "OK")
                                        } catch (e: Exception) {
                                            if (continuation.isActive) {
                                                continuation.resumeWithException(e)
                                            }
                                        }
                                    }
                                }
                                "error" -> {
                                    val error = message.optString("data", "Erreur inconnue")
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(IOException("ComfyUI error: $error"))
                                    }
                                    webSocket.close(1001, "Error")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur parsing WebSocket: ${e.message}", e)
                        }
                    }
                    
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket failure: ${t.message}", t)
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException("WebSocket failed: ${t.message}"))
                        }
                    }
                    
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closed: $code - $reason")
                    }
                })
                
                continuation.invokeOnCancellation {
                    webSocket.close(1000, "Cancelled")
                }
            }
        }
    
    /**
     * Récupère l'image générée depuis ComfyUI
     */
    private fun fetchGeneratedImage(promptId: String): String {
        // Récupérer l'historique pour trouver le nom du fichier
        val historyRequest = Request.Builder()
            .url("$COMFY_URL/history/$promptId")
            .get()
            .build()
        
        httpClient.newCall(historyRequest).execute().use { historyResponse ->
            if (!historyResponse.isSuccessful) {
                throw IOException("Failed to get history: ${historyResponse.code}")
            }
            
            val history = JSONObject(historyResponse.body?.string() ?: throw IOException("Empty history"))
            val promptData = history.getJSONObject(promptId)
            val outputs = promptData.getJSONObject("outputs")
            
            // Trouver le premier node avec des images
            val keys = outputs.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val node = outputs.getJSONObject(key)
                if (node.has("images")) {
                    val images = node.getJSONArray("images")
                    if (images.length() > 0) {
                        val imageInfo = images.getJSONObject(0)
                        val filename = imageInfo.getString("filename")
                        val subfolder = imageInfo.optString("subfolder", "")
                        val type = imageInfo.optString("type", "output")
                        
                        // Télécharger l'image
                        return downloadImage(filename, subfolder, type)
                    }
                }
            }
            
            throw IOException("No image found in output")
        }
    }
    
    /**
     * Télécharge une image depuis ComfyUI et la convertit en base64
     */
    private fun downloadImage(filename: String, subfolder: String, type: String): String {
        val url = buildString {
            append("$COMFY_URL/view")
            append("?filename=$filename")
            if (subfolder.isNotEmpty()) append("&subfolder=$subfolder")
            append("&type=$type")
        }
        
        val request = Request.Builder().url(url).get().build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download image: ${response.code}")
            }
            
            val bytes = response.body?.bytes() ?: throw IOException("Empty image")
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }
    
    /**
     * Crée un workflow ComfyUI basique pour txt2img
     */
    private fun createWorkflow(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Double
    ): JSONObject {
        val seed = (Math.random() * Int.MAX_VALUE).toLong()
        
        return JSONObject().apply {
            // KSampler
            put("3", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("seed", seed)
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
                put("_meta", JSONObject().apply { put("title", "KSampler") })
            })
            
            // CheckpointLoaderSimple
            put("4", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("ckpt_name", "sd_v15.safetensors") // Le modèle qu'on a (ou par défaut)
                })
                put("class_type", "CheckpointLoaderSimple")
                put("_meta", JSONObject().apply { put("title", "Load Checkpoint") })
            })
            
            // EmptyLatentImage - dimensions dynamiques
            put("5", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("width", width)
                    put("height", height)
                    put("batch_size", 1)
                })
                put("class_type", "EmptyLatentImage")
                put("_meta", JSONObject().apply { put("title", "Empty Latent Image") })
            })
            
            // CLIPTextEncode (positive)
            put("6", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("text", prompt)
                    put("clip", JSONArray().apply { put("4"); put(1) })
                })
                put("class_type", "CLIPTextEncode")
                put("_meta", JSONObject().apply { put("title", "CLIP Text Encode (Prompt)") })
            })
            
            // CLIPTextEncode (negative)
            put("7", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("text", negativePrompt)
                    put("clip", JSONArray().apply { put("4"); put(1) })
                })
                put("class_type", "CLIPTextEncode")
                put("_meta", JSONObject().apply { put("title", "CLIP Text Encode (Negative)") })
            })
            
            // VAEDecode
            put("8", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("samples", JSONArray().apply { put("3"); put(0) })
                    put("vae", JSONArray().apply { put("4"); put(2) })
                })
                put("class_type", "VAEDecode")
                put("_meta", JSONObject().apply { put("title", "VAE Decode") })
            })
            
            // SaveImage
            put("9", JSONObject().apply {
                put("inputs", JSONObject().apply {
                    put("filename_prefix", "naruto_ai")
                    put("images", JSONArray().apply { put("8"); put(0) })
                })
                put("class_type", "SaveImage")
                put("_meta", JSONObject().apply { put("title", "Save Image") })
            })
        }
    }
}
