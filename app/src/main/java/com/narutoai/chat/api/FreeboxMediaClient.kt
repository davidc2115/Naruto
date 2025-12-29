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
 * Client pour génération d'images via Freebox/Stable Horde/Pollination AI
 * v2.26.0: Choix d'API + fallback automatique
 * 
 * APIs disponibles:
 * - Freebox (ComfyUI): Local, privé, lent sur ARM CPU
 * - Stable Horde: Gratuit, illimité, NSFW, qualité SD 1.5/SDXL
 * - Pollination AI: Gratuit, rapide, NSFW, qualité variable
 */
class FreeboxMediaClient(
    private val preferredApi: String = "stable_horde"
) {
    private val pollinationClient = PollinationAIClient()
    private val stableHorde = StableHordeClient()
    private val comfyUIClient = ComfyUIClient()
    
    companion object {
        private const val TAG = "FreeboxMedia"
    }
    
    /**
     * Vérifie si ComfyUI sur Freebox est accessible
     */
    suspend fun isAvailable(): Boolean = comfyUIClient.isAvailable()
    
    /**
     * Génère une image selon l'API choisie par l'utilisateur
     * v2.26.0: Choix entre Freebox / Stable Horde / Pollination / Auto
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
            android.util.Log.d(TAG, "🎨 Génération image (API: $preferredApi)")
            
            when (preferredApi) {
                "freebox" -> {
                    // FREEBOX uniquement
                    android.util.Log.d(TAG, "🏠 Tentative Freebox")
                    val result = comfyUIClient.generateImage(prompt, negativePrompt, width, height, steps, cfgScale)
                    if (result.isSuccess) {
                        android.util.Log.d(TAG, "✅ Image générée via Freebox")
                    } else {
                        android.util.Log.w(TAG, "⚠️ Freebox échoué: ${result.exceptionOrNull()?.message}")
                    }
                    result
                }
                
                "stable_horde" -> {
                    // STABLE HORDE uniquement
                    android.util.Log.d(TAG, "⚡ Tentative Stable Horde")
                    val result = stableHorde.generateImage(prompt, negativePrompt, width, height, steps, cfgScale, isNSFW)
                    if (result.isSuccess) {
                        android.util.Log.d(TAG, "✅ Image générée via Stable Horde")
                    } else {
                        android.util.Log.w(TAG, "⚠️ Stable Horde échoué: ${result.exceptionOrNull()?.message}")
                    }
                    result
                }
                
                "pollination" -> {
                    // POLLINATION uniquement
                    android.util.Log.d(TAG, "🌸 Tentative Pollination AI")
                    val result = pollinationClient.generateImage(prompt, width, height, enhance = true)
                    if (result.isSuccess) {
                        android.util.Log.d(TAG, "✅ Image générée via Pollination AI")
                    } else {
                        android.util.Log.w(TAG, "⚠️ Pollination échoué: ${result.exceptionOrNull()?.message}")
                    }
                    result
                }
                
                else -> {
                    // AUTO: Freebox → Stable Horde → Pollination
                    android.util.Log.d(TAG, "🔄 Mode Auto (Freebox → Stable Horde → Pollination)")
                    
                    // Tentative 1: Freebox
                    android.util.Log.d(TAG, "🏠 Tentative 1/3: Freebox")
                    val freeboxResult = comfyUIClient.generateImage(prompt, negativePrompt, width, height, steps, cfgScale)
                    if (freeboxResult.isSuccess) {
                        android.util.Log.d(TAG, "✅ Image générée via Freebox")
                        return@withContext freeboxResult
                    }
                    android.util.Log.w(TAG, "⚠️ Freebox échoué, essai Stable Horde...")
                    
                    // Tentative 2: Stable Horde
                    android.util.Log.d(TAG, "⚡ Tentative 2/3: Stable Horde")
                    val stableHordeResult = stableHorde.generateImage(prompt, negativePrompt, width, height, steps, cfgScale, isNSFW)
                    if (stableHordeResult.isSuccess) {
                        android.util.Log.d(TAG, "✅ Image générée via Stable Horde")
                        return@withContext stableHordeResult
                    }
                    android.util.Log.w(TAG, "⚠️ Stable Horde échoué, essai Pollination...")
                    
                    // Tentative 3: Pollination AI
                    android.util.Log.d(TAG, "🌸 Tentative 3/3: Pollination AI")
                    val pollinationResult = pollinationClient.generateImage(prompt, width, height, enhance = true)
                    if (pollinationResult.isSuccess) {
                        android.util.Log.d(TAG, "✅ Image générée via Pollination AI")
                    } else {
                        android.util.Log.e(TAG, "❌ Toutes les APIs ont échoué")
                    }
                    pollinationResult
                }
            }
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
            val result = pollinationClient.generateVideo(
                prompt = enhancedPrompt,
                width = width,
                height = height,
                duration = duration,
                enhance = true,
                isNSFW = isNSFW
            )
            
            if (result.isFailure) {
                android.util.Log.e("FreeboxMedia", "❌ Erreur génération vidéo: ${result.exceptionOrNull()?.message}")
                Result.failure(result.exceptionOrNull() ?: Exception("Video generation failed"))
            } else {
                android.util.Log.d("FreeboxMedia", "✅ Vidéo générée via Pollination AI (${duration}s, ${width}x${height})")
                result
            }
            
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
