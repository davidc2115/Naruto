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
            
            android.util.Log.d(TAG, "✅ ComfyUI accessible! Génération locale...")
            
            // Utiliser le client ComfyUI avec WebSocket
            val result = comfyClient.generateImage(
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfgScale
            )
            
            result.getOrElse {
                android.util.Log.w(TAG, "Erreur ComfyUI: ${it.message}")
                android.util.Log.w(TAG, "🔄 FALLBACK: Utilisation Pollination AI")
                return@withContext pollinationFallback.generateImage(prompt, width, height, enhance = true)
            }
            
            android.util.Log.d(TAG, "✅ Image générée via ComfyUI Freebox !")
            android.util.Log.d(TAG, "📍 Source: ComfyUI (local, ARM CPU)")
            result
            
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
}
