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
 * Client pour génération d'images via Stable Horde ou Pollination AI
 * v2.23.0: Support des 2 APIs avec choix utilisateur
 * 
 * APIs disponibles:
 * - Stable Horde: Gratuit, illimité, NSFW, qualité SD 1.5/SDXL
 * - Pollination AI: Gratuit, rapide, NSFW, qualité variable
 */
class FreeboxMediaClient(
    private val pollinationClient: PollinationAIClient
) {
    
    private val stableHorde = StableHordeClient()
    private val comfyClient = ComfyUIClient()
    
    // Choix utilisateur : "stable_horde" ou "pollination"
    var preferredApi: String = "stable_horde" // Défaut: Stable Horde
    
    companion object {
        private const val TAG = "FreeboxMedia"
    }
    
    /**
     * Vérifie si ComfyUI sur Freebox est accessible
     */
    suspend fun isAvailable(): Boolean = comfyClient.isAvailable()
    
    /**
     * Génère une image selon l'API choisie par l'utilisateur
     * v2.23.0: Choix entre Stable Horde et Pollination AI
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
            // Choisir l'API selon la préférence utilisateur
            val primaryResult = when (preferredApi) {
                "stable_horde" -> {
                    android.util.Log.d(TAG, "🎨 API choisie: Stable Horde")
                    stableHorde.generateImage(prompt, negativePrompt, width, height, steps, cfgScale, isNSFW)
                }
                "pollination" -> {
                    android.util.Log.d(TAG, "🎨 API choisie: Pollination AI")
                    pollinationClient.generateImage(prompt, width, height, enhance = true)
                }
                else -> {
                    android.util.Log.d(TAG, "🎨 API par défaut: Stable Horde")
                    stableHorde.generateImage(prompt, negativePrompt, width, height, steps, cfgScale, isNSFW)
                }
            }
            
            primaryResult.getOrElse { error ->
                android.util.Log.w(TAG, "⚠️ API primaire échouée: ${error.message}")
                android.util.Log.w(TAG, "🔄 FALLBACK: Tentative API alternative...")
                
                // FALLBACK: Essayer l'autre API
                val fallbackResult = when (preferredApi) {
                    "stable_horde" -> pollinationClient.generateImage(prompt, width, height, enhance = true)
                    else -> stableHorde.generateImage(prompt, negativePrompt, width, height, steps, cfgScale, isNSFW)
                }
                
                return@withContext fallbackResult
            }
            
            android.util.Log.d(TAG, "✅ Image générée!")
            primaryResult
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Erreur génération: ${e.message}")
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
