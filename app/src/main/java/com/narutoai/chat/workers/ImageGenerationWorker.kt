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
            // Nettoyer les anciennes images (garder seulement les 50 dernières pour économiser l'espace)
            try {
                val imagesDir = java.io.File(applicationContext.filesDir, "generated_images")
                if (imagesDir.exists()) {
                    val imageFiles = imagesDir.listFiles { file -> 
                        file.name.startsWith("image_") && file.name.endsWith(".png")
                    }?.sortedByDescending { it.lastModified() } ?: emptyList()
                    
                    // Supprimer toutes sauf les 50 plus récentes
                    val toDelete = imageFiles.drop(50)
                    toDelete.forEach { file ->
                        file.delete()
                        android.util.Log.d("ImageWorker", "🗑️ Ancienne image supprimée: ${file.name}")
                    }
                    if (toDelete.isNotEmpty()) {
                        android.util.Log.d("ImageWorker", "🗑️ ${toDelete.size} anciennes images supprimées")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ImageWorker", "⚠️ Erreur nettoyage: ${e.message}")
            }
            
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
            val preferredApi = inputData.getString(KEY_PREFERRED_API) ?: "pollination"
            
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
                    // Freebox (ComfyUI local) - RÉACTIVÉ avec timeout augmenté
                    android.util.Log.d("ImageWorker", "🏠 Freebox/ComfyUI sélectionné")
                    android.util.Log.d("ImageWorker", "🔍 Test d'accessibilité de ComfyUI...")
                    
                    val comfyClient = ComfyUIClient()
                    
                    // Tester d'abord l'accessibilité
                    val isAvailable = try {
                        kotlinx.coroutines.runBlocking {
                            comfyClient.isAvailable()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ImageWorker", "❌ Erreur test ComfyUI: ${e.message}")
                        false
                    }
                    
                    if (!isAvailable) {
                        android.util.Log.w("ImageWorker", "⚠️ ComfyUI NON ACCESSIBLE (http://88.174.155.230:33437)")
                        android.util.Log.w("ImageWorker", "🔄 Fallback automatique vers Pollination AI...")
                        
                        // Afficher notification de fallback
                        NotificationHelper.showProgressNotification(
                            applicationContext,
                            NotificationHelper.NOTIFICATION_ID_IMAGE,
                            "Freebox inaccessible",
                            "ComfyUI non accessible. Utilisation de Pollination AI..."
                        )
                        
                        val pollinationClient = PollinationAIClient()
                        pollinationClient.generateImage(prompt, width, height, enhance = true)
                    } else {
                        android.util.Log.d("ImageWorker", "✅ ComfyUI accessible, génération...")
                        
                        // Essayer Freebox
                        val freeboxResult = comfyClient.generateImage(prompt, negativePrompt, width, height, steps, cfgScale)
                        if (freeboxResult.isSuccess) {
                            android.util.Log.d("ImageWorker", "✅ Freebox réussi (ComfyUI local)")
                            
                            // Afficher notification spécifique pour Freebox
                            NotificationHelper.showProgressNotification(
                                applicationContext,
                                NotificationHelper.NOTIFICATION_ID_IMAGE,
                                "Image générée ✅",
                                "Source: Freebox (ComfyUI local)"
                            )
                            
                            freeboxResult
                        } else {
                            val errorMsg = freeboxResult.exceptionOrNull()?.message ?: "Erreur inconnue"
                            android.util.Log.w("ImageWorker", "⚠️ Freebox ÉCHEC après test positif: $errorMsg")
                            android.util.Log.w("ImageWorker", "🔄 Fallback vers Pollination AI...")
                            
                            // Afficher notification de fallback
                            NotificationHelper.showProgressNotification(
                                applicationContext,
                                NotificationHelper.NOTIFICATION_ID_IMAGE,
                                "Erreur génération Freebox",
                                "Utilisation de Pollination AI..."
                            )
                            
                            val pollinationClient = PollinationAIClient()
                            pollinationClient.generateImage(prompt, width, height, enhance = true)
                        }
                    }
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
            
            // DEBUG: Logger l'URL générée
            result.onSuccess { url ->
                android.util.Log.d("ImageWorker", "🎨 URL générée: $url")
                android.util.Log.d("ImageWorker", "📏 Longueur URL: ${url.length} caractères")
            }
            
            result.fold(
                onSuccess = { imageUrl: String ->
                    android.util.Log.d("ImageWorker", "✅ Image générée: ${imageUrl.take(100)}")
                    
                    // Déterminer la source de l'image
                    val source = when {
                        preferredApi == "freebox" && !imageUrl.contains("pollinations") -> "Freebox (local)"
                        preferredApi == "pollination" || imageUrl.contains("pollinations") -> "Pollination AI (cloud)"
                        preferredApi == "stable_horde" -> "Stable Horde"
                        else -> "Cloud"
                    }
                    
                    android.util.Log.d("ImageWorker", "🎨 Source: $source")
                    
                    // Si c'est une URL Pollinations, la télécharger et sauvegarder en fichier PERMANENT
                    val finalImageUrl = if (imageUrl.contains("pollinations") || imageUrl.startsWith("http")) {
                        try {
                            android.util.Log.d("ImageWorker", "📥 Téléchargement image...")
                            val client = okhttp3.OkHttpClient.Builder()
                                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                            
                            val request = okhttp3.Request.Builder()
                                .url(imageUrl)
                                .get()
                                .build()
                            
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val imageBytes = response.body?.bytes()
                                    if (imageBytes != null && imageBytes.size > 1000) {
                                        // Sauvegarder dans filesDir (PERMANENT) au lieu de cacheDir (temporaire)
                                        val imagesDir = java.io.File(applicationContext.filesDir, "generated_images")
                                        imagesDir.mkdirs()
                                        
                                        val imageFile = java.io.File(imagesDir, "image_${System.currentTimeMillis()}.png")
                                        imageFile.writeBytes(imageBytes)
                                        android.util.Log.d("ImageWorker", "✅ Image sauvegardée PERMANENT: ${imageFile.absolutePath} (${imageBytes.size / 1024}KB)")
                                        imageFile.absolutePath
                                    } else {
                                        android.util.Log.w("ImageWorker", "⚠️ Image trop petite, utilisation URL")
                                        imageUrl
                                    }
                                } else {
                                    android.util.Log.w("ImageWorker", "⚠️ Échec téléchargement, utilisation URL")
                                    imageUrl
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ImageWorker", "❌ Erreur téléchargement: ${e.message}")
                            imageUrl
                        }
                    } else {
                        imageUrl
                    }
                    
                    // Sauvegarder le chemin dans SharedPreferences
                    val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(KEY_LATEST_IMAGE_URL, finalImageUrl).apply()
                    prefs.edit().putString("latest_image_source", source).apply()
                    android.util.Log.d("ImageWorker", "✅ Chemin sauvegardé dans SharedPrefs: ${finalImageUrl.take(100)}")
                    
                    // Notification de succès avec source
                    NotificationHelper.showSuccessNotification(
                        applicationContext,
                        NotificationHelper.NOTIFICATION_ID_IMAGE,
                        "Image générée ✅",
                        "Source: $source - Ouvrez l'app pour voir"
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
