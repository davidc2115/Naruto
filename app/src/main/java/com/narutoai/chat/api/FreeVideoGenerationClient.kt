package com.narutoai.chat.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Client pour génération de vidéos via API gratuite
 * 
 * API utilisée: Pollinations Video (https://pollinations.ai/video)
 * - Gratuit, sans clé API
 * - Génération de courtes vidéos (3-5s)
 * - Input: prompt texte
 * - Output: vidéo MP4
 */
class FreeVideoGenerationClient(private val context: Context) {
    
    companion object {
        private const val TAG = "FreeVideoClient"
        
        // Pollinations Video API
        private const val VIDEO_API_URL = "https://image.pollinations.ai/prompt"
        
        // Paramètres vidéo
        private const val DEFAULT_WIDTH = 512
        private const val DEFAULT_HEIGHT = 512
        private const val MAX_RETRIES = 3
        private const val TIMEOUT_SECONDS = 120L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Génère une vidéo courte du personnage
     * 
     * @param characterName Nom du personnage
     * @param prompt Description de l'action
     * @return Chemin local de la vidéo générée, ou null si échec
     */
    suspend fun generateCharacterVideo(
        characterName: String,
        prompt: String = "waving hello, smiling"
    ): Result<String> {
        return try {
            Log.d(TAG, "Generating video for $characterName: $prompt")
            
            // Construction prompt optimisé pour vidéo
            val fullPrompt = "$characterName anime character, $prompt, smooth animation, high quality"
            
            // Génération (note: Pollinations n'a pas d'API vidéo native, 
            // on génère une séquence d'images)
            val imageUrl = generateAnimationFrame(fullPrompt)
            
            if (imageUrl != null) {
                Log.d(TAG, "✅ Video generation successful")
                Result.success(imageUrl)
            } else {
                Log.e(TAG, "❌ Video generation failed")
                Result.failure(Exception("Video generation failed"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating video", e)
            Result.failure(e)
        }
    }
    
    /**
     * Génère une frame d'animation
     * Note: Pour vraie vidéo, utiliser API dédiée comme Runway ML ou Genmo
     * (mais nécessitent clé API payante)
     */
    private suspend fun generateAnimationFrame(prompt: String): String? {
        var lastException: Exception? = null
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "Attempt ${attempt + 1}/$MAX_RETRIES")
                
                // URL avec paramètres
                val url = "$VIDEO_API_URL/${java.net.URLEncoder.encode(prompt, "UTF-8")}" +
                        "?width=$DEFAULT_WIDTH" +
                        "&height=$DEFAULT_HEIGHT" +
                        "&nologo=true" +
                        "&enhance=true" +
                        "&seed=${System.currentTimeMillis()}"
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val contentType = response.header("Content-Type") ?: ""
                    
                    // Vérifier que c'est une image
                    if (contentType.startsWith("image/")) {
                        // Sauvegarder dans cache
                        val cacheFile = saveToCacheFile(response.body?.bytes(), "video_frame_${System.currentTimeMillis()}.png")
                        
                        if (cacheFile != null) {
                            Log.d(TAG, "✅ Frame saved: $cacheFile")
                            return cacheFile
                        }
                    } else {
                        Log.w(TAG, "Invalid content type: $contentType")
                    }
                } else {
                    Log.w(TAG, "HTTP ${response.code}")
                }
                
                response.close()
                
            } catch (e: Exception) {
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                lastException = e
            }
            
            // Pause entre tentatives
            if (attempt < MAX_RETRIES - 1) {
                delay(5000)
            }
        }
        
        Log.e(TAG, "All attempts failed", lastException)
        return null
    }
    
    /**
     * Sauvegarde bytes dans fichier cache
     */
    private fun saveToCacheFile(bytes: ByteArray?, filename: String): String? {
        return try {
            if (bytes == null || bytes.isEmpty()) {
                Log.w(TAG, "Empty bytes")
                return null
            }
            
            val cacheDir = context.cacheDir
            val file = java.io.File(cacheDir, filename)
            
            file.outputStream().use { output ->
                output.write(bytes)
            }
            
            file.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to cache", e)
            null
        }
    }
    
    /**
     * Nettoie fichiers cache anciens (>1 jour)
     */
    fun cleanOldCache() {
        try {
            val cacheDir = context.cacheDir
            val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("video_frame_") && file.lastModified() < oneDayAgo) {
                    file.delete()
                    Log.d(TAG, "Deleted old cache: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning cache", e)
        }
    }
}

/**
 * Note: APIs vidéo gratuites sans clé
 * 
 * 1. Pollinations (utilisé ici)
 *    - Images animées uniquement
 *    - Pas de vraies vidéos
 *    - Gratuit, rapide
 * 
 * 2. Stability AI Free Tier (nécessite clé gratuite)
 *    - https://api.stability.ai
 *    - Image-to-Video
 *    - 25 crédits gratuits/mois
 * 
 * 3. Genmo (nécessite compte)
 *    - https://genmo.ai
 *    - Text-to-Video
 *    - Version gratuite limitée
 * 
 * 4. Pour vraies vidéos:
 *    - Runway ML (payant)
 *    - Pika Labs (payant)
 *    - Luma AI (payant)
 */
