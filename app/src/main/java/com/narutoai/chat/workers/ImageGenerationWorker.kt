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
        
        private const val PREFS_NAME = "image_worker_results"
        private const val KEY_LATEST_IMAGE_URL = "latest_image_url"
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
            val steps = inputData.getInt(KEY_STEPS, 15) // Réduit de 20 à 15 par défaut
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
                    // Freebox temporairement désactivée (inaccessible depuis Internet)
                    // Fallback vers Pollination AI (plus rapide que Stable Horde)
                    android.util.Log.d("ImageWorker", "⚠️ Freebox désactivée, utilisation de Pollination AI")
                    val pollinationClient = PollinationAIClient()
                    pollinationClient.generateImage(prompt, width, height, enhance = true)
                }
                "stable_horde" -> {
                    // Stable Horde (gratuit mais LENT - queue de plusieurs minutes)
                    android.util.Log.d("ImageWorker", "⏳ Stable Horde sélectionné (peut être lent)")
                    val stableHordeClient = StableHordeClient()
                    stableHordeClient.generateImage(prompt, negativePrompt, width, height, steps, cfgScale, isNSFW)
                }
                "pollination" -> {
                    // Pollination AI (rapide - 10-20 secondes)
                    android.util.Log.d("ImageWorker", "⚡ Pollination AI sélectionné (rapide)")
                    val pollinationClient = PollinationAIClient()
                    pollinationClient.generateImage(prompt, width, height, enhance = true)
                }
                else -> {
                    // Auto: Pollination AI par défaut (plus rapide)
                    android.util.Log.d("ImageWorker", "⚡ Mode auto: utilisation de Pollination AI (rapide)")
                    val pollinationClient = PollinationAIClient()
                    pollinationClient.generateImage(prompt, width, height, enhance = true)
                }
            }
            
            result.fold(
                onSuccess = { imageUrl: String ->
                    android.util.Log.d("ImageWorker", "✅ Image générée: ${imageUrl.take(100)}")
                    
                    // Sauvegarder l'URL dans SharedPreferences (pas de limite)
                    val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(KEY_LATEST_IMAGE_URL, imageUrl).apply()
                    android.util.Log.d("ImageWorker", "✅ URL sauvegardée dans SharedPrefs (${imageUrl.length} chars)")
                    
                    // Notification de succès
                    NotificationHelper.showSuccessNotification(
                        applicationContext,
                        NotificationHelper.NOTIFICATION_ID_IMAGE,
                        "Image générée ✅",
                        "Votre image est prête ! Ouvrez l'app pour la voir."
                    )
                    
                    // Retourner uniquement un flag de succès (pas l'URL)
                    val outputData = workDataOf(KEY_RESULT_URL to "success")
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
