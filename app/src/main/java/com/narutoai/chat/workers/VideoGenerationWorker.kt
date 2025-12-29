package com.narutoai.chat.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.narutoai.chat.api.FreeboxMediaClient
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
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
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
                "Génération de vidéo",
                "Création de votre vidéo en cours (peut prendre 1-2 min)..."
            )
            
            // Générer la vidéo avec Pollination AI
            val pollinationClient = PollinationAIClient()
            val result = pollinationClient.generateVideo(
                prompt = prompt,
                width = width,
                height = height,
                duration = duration,
                enhance = true,
                isNSFW = isNSFW
            )
            
            result.fold(
                onSuccess = { videoUrl ->
                    // Notification de succès
                    NotificationHelper.showSuccessNotification(
                        applicationContext,
                        NotificationHelper.NOTIFICATION_ID_VIDEO,
                        "Vidéo générée ✅",
                        "Votre vidéo est prête ! Ouvrez l'app pour la voir."
                    )
                    
                    val outputData = workDataOf(KEY_RESULT_URL to videoUrl)
                    Result.success(outputData)
                },
                onFailure = { exception ->
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
