package com.narutoai.chat.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.narutoai.chat.api.PollinationAIClient
import com.narutoai.chat.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker pour génération de vidéo en arrière-plan
 * v2.26.0: Génération asynchrone avec notification
 */
class VideoGenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val KEY_PROMPT = "prompt"
        const val KEY_NEGATIVE_PROMPT = "negative_prompt"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_DURATION = "duration"
        const val KEY_IS_NSFW = "is_nsfw"
        const val KEY_PREFERRED_API = "preferred_api"
        
        const val KEY_RESULT_URL = "result_url"
        const val KEY_ERROR = "error"
        
        private const val PREFS_NAME = "video_worker_results"
        private const val KEY_LATEST_VIDEO_URL = "latest_video_url"
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Nettoyer les anciennes vidéos (garder seulement les 20 dernières)
            try {
                val videosDir = java.io.File(applicationContext.filesDir, "generated_videos")
                if (videosDir.exists()) {
                    val videoFiles = videosDir.listFiles { file -> 
                        file.name.startsWith("video_") && (file.name.endsWith(".mp4") || file.name.endsWith(".gif"))
                    }?.sortedByDescending { it.lastModified() } ?: emptyList()
                    
                    val toDelete = videoFiles.drop(20)
                    toDelete.forEach { it.delete() }
                    if (toDelete.isNotEmpty()) {
                        android.util.Log.d("VideoWorker", "🗑️ ${toDelete.size} anciennes vidéos supprimées")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoWorker", "⚠️ Erreur nettoyage: ${e.message}")
            }
            
            // Créer canal de notification
            NotificationHelper.createNotificationChannel(applicationContext)
            
            // Récupérer paramètres
            val prompt = inputData.getString(KEY_PROMPT) ?: return@withContext Result.failure()
            val negativePrompt = inputData.getString(KEY_NEGATIVE_PROMPT) ?: "low quality, blurry"
            val width = inputData.getInt(KEY_WIDTH, 512)
            val height = inputData.getInt(KEY_HEIGHT, 512)
            val duration = inputData.getInt(KEY_DURATION, 5)
            val isNSFW = inputData.getBoolean(KEY_IS_NSFW, false)
            
            // Afficher notification de progression
            NotificationHelper.showProgressNotification(
                applicationContext,
                NotificationHelper.NOTIFICATION_ID_VIDEO,
                "Génération de GIF animé",
                "Création de votre animation (30 secondes environ)..."
            )
            
            // 🆕 NOUVELLE SOLUTION: Générer image puis animer en GIF
            // 1. Générer une image avec Pollination AI
            val pollinationClient = PollinationAIClient()
            val imageResult = pollinationClient.generateImage(
                prompt = prompt,
                width = width,
                height = height,
                model = "flux", // Meilleure qualité pour animation
                enhance = true
            )
            
            if (imageResult.isFailure) {
                val errorMsg = imageResult.exceptionOrNull()?.message ?: "Échec génération image"
                android.util.Log.e("VideoWorker", "❌ $errorMsg")
                
                NotificationHelper.showErrorNotification(
                    applicationContext,
                    NotificationHelper.NOTIFICATION_ID_VIDEO,
                    "Erreur génération image ❌",
                    errorMsg
                )
                
                val outputData = workDataOf(KEY_ERROR to errorMsg)
                return@withContext Result.failure(outputData)
            }
            
            val imageUrl = imageResult.getOrNull()
            if (imageUrl == null) {
                android.util.Log.e("VideoWorker", "❌ URL image vide")
                
                NotificationHelper.showErrorNotification(
                    applicationContext,
                    NotificationHelper.NOTIFICATION_ID_VIDEO,
                    "Erreur génération image ❌",
                    "URL image vide"
                )
                
                val outputData = workDataOf(KEY_ERROR to "URL image vide")
                return@withContext Result.failure(outputData)
            }
            
            android.util.Log.d("VideoWorker", "✅ Image générée: $imageUrl")
            
            // 2. Créer un GIF animé à partir de l'image
            val gifClient = com.narutoai.chat.api.GifAnimationClient()
            val videosDir = java.io.File(applicationContext.filesDir, "generated_videos")
            videosDir.mkdirs()
            val gifFile = java.io.File(videosDir, "video_${System.currentTimeMillis()}.gif")
            
            val result = gifClient.createAnimatedGif(
                imageUrl = imageUrl,
                outputFile = gifFile,
                animationType = "ken_burns", // Effet cinématique (zoom + pan)
                durationMs = duration * 1000 // Convertir secondes en ms
            )
            
            result.fold(
                onSuccess = { gifPath: String ->
                    android.util.Log.d("VideoWorker", "✅ GIF animé créé: ${gifPath.take(100)}")
                    
                    // Le fichier GIF est déjà sauvegardé localement
                    val finalVideoUrl = gifPath
                    
                    // Sauvegarder dans SharedPreferences
                    val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(KEY_LATEST_VIDEO_URL, finalVideoUrl).apply()
                    android.util.Log.d("VideoWorker", "✅ Chemin GIF sauvegardé: ${finalVideoUrl.take(100)}")
                    
                    // Notification de succès
                    NotificationHelper.showSuccessNotification(
                        applicationContext,
                        NotificationHelper.NOTIFICATION_ID_VIDEO,
                        "Vidéo animée générée ✅",
                        "Votre GIF animé est prêt ! Ouvrez l'app pour le voir."
                    )
                    
                    val outputData = workDataOf(KEY_RESULT_URL to "success")
                    Result.success(outputData)
                },
                onFailure = { exception: Throwable ->
                    // Notification d'erreur
                    NotificationHelper.showErrorNotification(
                        applicationContext,
                        NotificationHelper.NOTIFICATION_ID_VIDEO,
                        "Erreur génération vidéo ❌",
                        exception.message ?: "Erreur inconnue"
                    )
                    
                    val outputData = workDataOf(KEY_ERROR to (exception.message ?: "Unknown error"))
                    Result.failure(outputData)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("VideoWorker", "Erreur: ${e.message}", e)
            
            NotificationHelper.showErrorNotification(
                applicationContext,
                NotificationHelper.NOTIFICATION_ID_VIDEO,
                "Erreur génération vidéo ❌",
                e.message ?: "Erreur inconnue"
            )
            
            val outputData = workDataOf(KEY_ERROR to (e.message ?: "Unknown error"))
            Result.failure(outputData)
        }
    }
}
