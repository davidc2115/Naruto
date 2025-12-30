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
                onSuccess = { videoUrl: String ->
                    android.util.Log.d("VideoWorker", "✅ Vidéo générée: ${videoUrl.take(100)}")
                    
                    // Télécharger et sauvegarder en fichier permanent
                    val finalVideoUrl = if (videoUrl.startsWith("http")) {
                        try {
                            android.util.Log.d("VideoWorker", "📥 Téléchargement vidéo...")
                            val client = okhttp3.OkHttpClient.Builder()
                                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                            
                            val request = okhttp3.Request.Builder()
                                .url(videoUrl)
                                .get()
                                .build()
                            
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val videoBytes = response.body?.bytes()
                                    if (videoBytes != null && videoBytes.size > 10000) {
                                        // Sauvegarder dans filesDir (PERMANENT)
                                        val videosDir = java.io.File(applicationContext.filesDir, "generated_videos")
                                        videosDir.mkdirs()
                                        
                                        val videoFile = java.io.File(videosDir, "video_${System.currentTimeMillis()}.mp4")
                                        videoFile.writeBytes(videoBytes)
                                        android.util.Log.d("VideoWorker", "✅ Vidéo sauvegardée PERMANENT: ${videoFile.absolutePath} (${videoBytes.size / 1024}KB)")
                                        videoFile.absolutePath
                                    } else {
                                        android.util.Log.w("VideoWorker", "⚠️ Vidéo trop petite")
                                        videoUrl
                                    }
                                } else {
                                    android.util.Log.w("VideoWorker", "⚠️ Échec téléchargement")
                                    videoUrl
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VideoWorker", "❌ Erreur téléchargement: ${e.message}")
                            videoUrl
                        }
                    } else {
                        videoUrl
                    }
                    
                    // Sauvegarder dans SharedPreferences
                    val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(KEY_LATEST_VIDEO_URL, finalVideoUrl).apply()
                    android.util.Log.d("VideoWorker", "✅ Chemin vidéo sauvegardé: ${finalVideoUrl.take(100)}")
                    
                    // Notification de succès
                    NotificationHelper.showSuccessNotification(
                        applicationContext,
                        NotificationHelper.NOTIFICATION_ID_VIDEO,
                        "Vidéo générée ✅",
                        "Votre vidéo est prête ! Ouvrez l'app pour la voir."
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
