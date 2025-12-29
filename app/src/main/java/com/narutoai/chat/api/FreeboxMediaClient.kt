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
 * Client pour génération d'images via Stable Horde (GRATUIT, ILLIMITÉ)
 * v2.23.0: Stable Horde remplace Pollination AI (plus stable, NSFW supporté)
 * 
 * Stable Horde: Réseau décentralisé Stable Diffusion
 * - 100% gratuit
 * - Pas de clé API requise
 * - Support NSFW
 * - Pas de rate limit
 */
class FreeboxMediaClient(
    private val pollinationFallback: PollinationAIClient
) {
    
    private val stableHorde = StableHordeClient() // Nouvelle API principale
    private val comfyClient = ComfyUIClient()
    
    companion object {
        private const val TAG = "FreeboxMedia"
    }
    
    /**
     * Vérifie si ComfyUI sur Freebox est accessible
     */
    suspend fun isAvailable(): Boolean = comfyClient.isAvailable()
    
    /**
     * Génère une image via Stable Horde (gratuit, illimité, NSFW supporté)
     * v2.23.0: Stable Horde remplace Pollination AI
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
            android.util.Log.d("FreeboxMedia", "🎨 Génération via Stable Horde (gratuit, illimité)...")
            
            // PRIORITÉ 1: Stable Horde (gratuit, fiable, NSFW ok)
            val result = stableHorde.generateImage(
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfgScale,
                nsfw = isNSFW
            )
            
            result.getOrElse { error ->
                android.util.Log.w("FreeboxMedia", "⚠️ Stable Horde échoué: ${error.message}")
                android.util.Log.w("FreeboxMedia", "🔄 FALLBACK: Tentative Pollination AI...")
                
                // FALLBACK: Essayer Pollination AI
                return@withContext pollinationFallback.generateImage(
                    prompt = prompt,
                    width = width,
                    height = height,
                    enhance = true
                )
            }
            
            android.util.Log.d("FreeboxMedia", "✅ Image générée via Stable Horde !")
            result
            
        } catch (e: Exception) {
            android.util.Log.e("FreeboxMedia", "❌ Erreur génération: ${e.message}")
            
            // Dernier fallback: Pollination AI
            pollinationFallback.generateImage(prompt, width, height, enhance = true)
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
