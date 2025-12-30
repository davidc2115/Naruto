package com.narutoai.chat.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Client pour génération d'images via Pollination AI
 * v2.34.0: Simplifié - utilise uniquement Pollination AI
 */
class FreeboxMediaClient {
    private val pollinationClient = PollinationAIClient()
    
    companion object {
        private const val TAG = "FreeboxMedia"
    }
    
    /**
     * Vérifie si le service est disponible
     */
    suspend fun isAvailable(): Boolean = true
    
    /**
     * Génère une image via Pollination AI
     */
    suspend fun generateImage(
        prompt: String,
        negativePrompt: String = "low quality, blurry",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Double = 7.0,
        isNSFW: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "🎨 Génération image via Pollination AI")
            
            val result = pollinationClient.generateImage(prompt, width, height, enhance = true)
            
            if (result.isSuccess) {
                android.util.Log.d(TAG, "✅ Image générée via Pollination AI")
            } else {
                android.util.Log.w(TAG, "⚠️ Pollination échoué: ${result.exceptionOrNull()?.message}")
            }
            
            result
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Erreur génération", e)
            Result.failure(e)
        }
    }
}
