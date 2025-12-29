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
     * v2.23.1: Désactive Freebox, utilise uniquement Stable Horde et Pollination (URLs)
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
            android.util.Log.d(TAG, "🎨 Génération image (Stable Horde → Pollination)")
            
            // PRIORITÉ 1: Stable Horde (gratuit, illimité, URLs)
            val stableHordeResult = stableHorde.generateImage(
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfgScale,
                nsfw = isNSFW
            )
            
            if (stableHordeResult.isSuccess) {
                android.util.Log.d(TAG, "✅ Image générée via Stable Horde")
                return@withContext stableHordeResult
            }
            
            // FALLBACK: Pollination AI (URLs)
            android.util.Log.w(TAG, "⚠️ Stable Horde échoué, fallback Pollination...")
            val pollinationResult = pollinationClient.generateImage(
                prompt = prompt,
                width = width,
                height = height,
                enhance = true
            )
            
            if (pollinationResult.isSuccess) {
                android.util.Log.d(TAG, "✅ Image générée via Pollination AI")
            } else {
                android.util.Log.e(TAG, "❌ Toutes les APIs ont échoué")
            }
            
            pollinationResult
            
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
