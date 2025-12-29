package com.narutoai.chat.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.narutoai.chat.api.ComfyUIClient
import com.narutoai.chat.api.PollinationAIClient
import com.narutoai.chat.api.StableHordeClient
import com.narutoai.chat.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker pour génération d'image en arrière-plan
 * v2.26.0: Génération asynchrone avec notification
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
            val steps = inputData.getInt(KEY_STEPS, 20)
            val cfgScale = inputData.getDouble(KEY_CFG_SCALE, 7.0)
            val isNSFW = inputData.getBoolean(KEY_IS_NSFW, false)
            val preferredApi = inputData.getString(KEY_PREFERRED_API) ?: "stable_horde"
            
            // Afficher notification de progression
            NotificationHelper.showProgressNotification(
                applicationContext,
                NotificationHelper.NOTIFICATION_ID_IMAGE,
                "Génération d'image",
                "Création de votre image en cours..."
            )
            
            // Générer l'image avec l'API choisie
            val result = when (preferredApi) {
                "freebox" -> {
                    val comfyClient = ComfyUIClient()
                    comfyClient.generateImage(prompt, negativePrompt, width, height, steps, cfgScale)
                }
                "pollination" -> {
                    val pollinationClient = PollinationAIClient()
                    pollinationClient.generateImage(prompt, width, height, enhance = true)
                }
                else -> {
                    // stable_horde ou auto
                    val stableHordeClient = StableHordeClient()
                    stableHordeClient.generateImage(prompt, negativePrompt, width, height, steps, cfgScale, isNSFW)
                }
            }
            
            result.fold(
                onSuccess = { imageUrl: String ->
                    android.util.Log.d("ImageWorker", "✅ Image générée: ${imageUrl.take(100)}")
                    
                    // Notification de succès
                    NotificationHelper.showSuccessNotification(
                        applicationContext,
                        NotificationHelper.NOTIFICATION_ID_IMAGE,
                        "Image générée ✅",
                        "Votre image est prête ! Ouvrez l'app pour la voir."
                    )
                    
                    val outputData = workDataOf(KEY_RESULT_URL to imageUrl)
                    android.util.Log.d("ImageWorker", "✅ Output data créé avec URL")
                    Result.success(outputData)
                },
                onFailure = { exception: Throwable ->
                    android.util.Log.e("ImageWorker", "❌ Erreur: ${exception.message}", exception)
                    
                    // Notification d'erreur
                    NotificationHelper.showErrorNotification(
                        applicationContext,
                        NotificationHelper.NOTIFICATION_ID_IMAGE,
                        "Erreur génération ❌",
                        exception.message ?: "Erreur inconnue"
                    )
                    
                    val outputData = workDataOf(KEY_ERROR to (exception.message ?: "Unknown error"))
                    Result.failure(outputData)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("ImageWorker", "Erreur: ${e.message}", e)
            
            NotificationHelper.showErrorNotification(
                applicationContext,
                NotificationHelper.NOTIFICATION_ID_IMAGE,
                "Erreur génération ❌",
                e.message ?: "Erreur inconnue"
            )
            
            val outputData = workDataOf(KEY_ERROR to (e.message ?: "Unknown error"))
            Result.failure(outputData)
        }
    }
}
