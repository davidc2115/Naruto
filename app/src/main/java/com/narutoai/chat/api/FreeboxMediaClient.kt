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
 * v2.17.0: ComfyUI avec WebSocket fonctionnel !
 */
class FreeboxMediaClient(private val pollinationFallback: PollinationAIClient) {
    
    private val comfyClient = ComfyUIClient()
    
    companion object {
        private const val TAG = "FreeboxMedia"
    }
    
    /**
     * Vérifie si ComfyUI sur Freebox est accessible
     */
    suspend fun isAvailable(): Boolean = comfyClient.isAvailable()
    
    /**
     * Génère une image via Pollination AI directement
     * CHANGEMENT v2.22.0: Pollination AI uniquement (Freebox trop lent/instable)
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
            android.util.Log.d("FreeboxMedia", "🎨 Génération d'image via Pollination AI...")
            
            // Utiliser directement Pollination AI (plus rapide et fiable)
            val result = pollinationFallback.generateImage(
                prompt = prompt,
                width = width,
                height = height,
                enhance = true
            )
            
            result.getOrElse { error ->
                android.util.Log.e("FreeboxMedia", "❌ Erreur Pollination AI: ${error.message}")
                return@withContext Result.failure(error)
            }
            
            android.util.Log.d("FreeboxMedia", "✅ Image générée via Pollination AI !")
            result
            
        } catch (e: Exception) {
            android.util.Log.e("FreeboxMedia", "❌ Erreur génération: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Génère une vidéo MP4 via Pollination AI
     * NOTE: La Freebox n'a pas assez de ressources pour la génération vidéo
     * On utilise directement Pollination AI Video (gratuit, sans limite)
     */
    suspend fun generateVideo(
        prompt: String,
        negativePrompt: String = "low quality, blurry, distorted, ugly, deformed, static",
        width: Int = 512,
        height: Int = 512,
        duration: Int = 5, // 5 secondes
        isNSFW: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FreeboxMedia", "🎬 Génération vidéo via Pollination AI Video...")
            
            // Enrichir le prompt avec le negative prompt et détails
            val enhancedPrompt = buildString {
                append(prompt)
                append(", smooth motion, cinematic, fluid animation")
                if (!prompt.contains("4k") && !prompt.contains("quality")) {
                    append(", high quality, professional")
                }
            }
            
            // Utiliser Pollination AI Video (supporte SFW et NSFW)
            val result = pollinationFallback.generateVideo(
                prompt = enhancedPrompt,
                width = width,
                height = height,
                duration = duration,
                enhance = true,
                isNSFW = isNSFW
            )
            
            result.getOrElse { error ->
                android.util.Log.e("FreeboxMedia", "❌ Erreur génération vidéo: ${error.message}")
                return@withContext Result.failure(error)
            }
            
            android.util.Log.d("FreeboxMedia", "✅ Vidéo générée via Pollination AI (${duration}s, ${width}x${height})")
            result
            
        } catch (e: Exception) {
            android.util.Log.e("FreeboxMedia", "❌ Erreur vidéo: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Retourne les modèles disponibles sur ComfyUI
     */
    suspend fun getAvailableModels(): List<String> = withContext(Dispatchers.IO) {
        try {
            if (isAvailable()) {
                listOf("ComfyUI Default (sd_v15.safetensors)")
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
