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
 * Worker pour génération d'image en arrière-plan
 * v2.34.0: Utilise uniquement Pollination AI
 */
class ImageGenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val KEY_PROMPT = "prompt"
        const val KEY_NEGATIVE_PROMPT = "negative_prompt"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_STEPS = "steps"
        const val KEY_CFG_SCALE = "cfg_scale"
        const val KEY_IS_NSFW = "is_nsfw"
        const val KEY_IMAGE_PATH = "image_path"
    }
    
    private val pollinationClient = PollinationAIClient()
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Récupérer paramètres
            val prompt = inputData.getString(KEY_PROMPT) ?: return@withContext Result.failure()
            val width = inputData.getInt(KEY_WIDTH, 512)
            val height = inputData.getInt(KEY_HEIGHT, 512)
            
            android.util.Log.d("ImageWorker", "🎨 Génération: $prompt")
            
            // Afficher notification de début
            NotificationHelper.showProgressNotification(
                applicationContext,
                NotificationHelper.NOTIFICATION_ID_IMAGE,
                "Génération en cours...",
                "Pollination AI"
            )
            
            // Générer l'image via Pollination AI
            val result = pollinationClient.generateImage(prompt, width, height, enhance = true)
            
            if (result.isSuccess) {
                val imagePath = result.getOrNull()
                android.util.Log.d("ImageWorker", "✅ Image générée: $imagePath")
                
                // Sauvegarder dans SharedPreferences pour que ChatViewModel puisse le récupérer
                val prefs = applicationContext.getSharedPreferences("image_worker_results", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("latest_image_url", imagePath)
                    putString("latest_image_source", "Pollination AI")
                    apply()
                }
                
                android.util.Log.d("ImageWorker", "💾 URL sauvegardée dans SharedPrefs: ${imagePath?.take(100)}")
                
                // Notification de succès
                NotificationHelper.showProgressNotification(
                    applicationContext,
                    NotificationHelper.NOTIFICATION_ID_IMAGE,
                    "Image générée ✅",
                    "Pollination AI"
                )
                
                // Retourner résultat
                val outputData = workDataOf(KEY_IMAGE_PATH to imagePath)
                Result.success(outputData)
            } else {
                android.util.Log.e("ImageWorker", "❌ Échec: ${result.exceptionOrNull()?.message}")
                
                // Notification d'échec
                NotificationHelper.showProgressNotification(
                    applicationContext,
                    NotificationHelper.NOTIFICATION_ID_IMAGE,
                    "Échec de la génération ❌",
                    result.exceptionOrNull()?.message ?: "Erreur inconnue"
                )
                
                Result.failure()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ImageWorker", "Erreur génération", e)
            
            // Notification d'erreur
            NotificationHelper.showProgressNotification(
                applicationContext,
                NotificationHelper.NOTIFICATION_ID_IMAGE,
                "Erreur ❌",
                e.message ?: "Erreur inconnue"
            )
            
            Result.failure()
        }
    }
}
