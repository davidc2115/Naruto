package com.narutoai.chat.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client pour Pollination AI
 * API de génération d'images hyper-réalistes
 * https://pollinations.ai/
 */
class PollinationAIClient {
    
    // Configuration HTTP avec timeout augmenté et retry automatique
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)  // Gardé à 60s
        .readTimeout(120, TimeUnit.SECONDS)    // Gardé à 120s
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)        // Retry automatique sur échec connexion
        .build()
    
    companion object {
        // API Pollination - Gratuite et sans clé!
        private const val BASE_URL = "https://image.pollinations.ai/prompt"
        // TEMPORAIRE: Vidéo désactivée (problème DNS), fallback sur image
        private const val VIDEO_BASE_URL = "https://image.pollinations.ai/prompt" // Fallback temporaire
        
        // Configuration par défaut
        private const val DEFAULT_WIDTH = 512
        private const val DEFAULT_HEIGHT = 768
        private const val DEFAULT_MODEL = "turbo" // Plus rapide
        private const val DEFAULT_VIDEO_MODEL = "dreamshaper" // Modèle vidéo par défaut
    }
    
    /**
     * Génère une image hyper-réaliste avec Pollination AI
     * @param prompt Description détaillée
     * @param width Largeur de l'image
     * @param height Hauteur de l'image
     * @param model Modèle à utiliser (turbo, flux, etc.)
     * @return URL directe de l'image générée
     */
    suspend fun generateImage(
        prompt: String,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        model: String = DEFAULT_MODEL,
        enhance: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Délai minimal pour éviter spam (2s au lieu de 10s pour meilleure UX)
            delay(2000)
            
            // Retry avec backoff exponentiel pour gérer 500/502/503
            var lastException: Exception? = null
            val maxRetries = 5 // 5 tentatives avec backoff long
            
            for (attempt in 1..maxRetries) {
                try {
                    // Pollination AI utilise une API simple par URL
                    // Format: https://image.pollinations.ai/prompt/{prompt}?width={w}&height={h}
                    
                    // Encoder le prompt pour URL
                    val encodedPrompt = java.net.URLEncoder.encode(
                        if (enhance) enhancePrompt(prompt) else prompt,
                        "UTF-8"
                    )
                    
                    val imageUrl = buildString {
                        append(BASE_URL)
                        append("/")
                        append(encodedPrompt)
                        append("?width=$width")
                        append("&height=$height")
                        append("&model=$model")
                        append("&nologo=true") // Sans watermark
                        append("&enhance=true") // Amélioration automatique
                        // Seed unique pour éviter cache et limiter les 429
                        append("&seed=${System.currentTimeMillis()}")
                    }
                    
                    // Vérifier que l'image est accessible (GET complet pour vraiment télécharger)
                    val request = Request.Builder()
                        .url(imageUrl)
                        .get() // GET complet au lieu de HEAD
                        .build()
                    
                    client.newCall(request).execute().use { response ->
                        when (response.code) {
                            200 -> {
                                // Vérifier que le body n'est pas vide
                                val contentLength = response.body?.contentLength() ?: 0
                                if (contentLength > 1000) { // Au moins 1KB
                                    return@withContext Result.success(imageUrl)
                                } else {
                                    throw IOException("Image trop petite ou invalide")
                                }
                            }
                            429 -> {
                                // Rate limit - attendre TRÈS longtemps
                                if (attempt < maxRetries) {
                                    delay(20000L * attempt) // 20s, 40s, 60s, 80s, 100s
                                    lastException = IOException("Rate limit 429 (tentative $attempt/$maxRetries)")
                                    // Continue to next retry
                                } else {
                                    return@withContext Result.failure(
                                        IOException("❌ Rate limit 429 - Trop de requêtes. Réessayez dans 2 minutes.")
                                    )
                                }
                            }
                            500, 502, 503, 504 -> {
                                // Internal Server Error / Bad Gateway / Service Unavailable - retry
                                if (attempt < maxRetries) {
                                    delay(15000L * attempt) // 15s, 30s, 45s, 60s, 75s
                                    lastException = IOException("Erreur serveur ${response.code} (tentative $attempt/$maxRetries)")
                                    // Continue to next retry
                                } else {
                                    return@withContext Result.failure(
                                        IOException("❌ Erreur ${response.code} - Service Pollinations AI surchargé. Réessayez dans quelques minutes.")
                                    )
                                }
                            }
                            else -> {
                                return@withContext Result.failure(
                                    IOException("Erreur génération: HTTP ${response.code}")
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < maxRetries) {
                        // Backoff exponentiel
                        delay(3000L * attempt)
                    }
                }
            }
            
            // Si on arrive ici, tous les retries ont échoué
            Result.failure(lastException ?: IOException("Échec génération après $maxRetries tentatives"))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Génère un portrait de personnage hyper-réaliste
     * @param characterName Nom du personnage
     * @param physicalDescription Description physique détaillée
     * @param style Style de l'image (realistic, anime, etc.)
     * @param gender Genre (male, female)
     * @return URL de l'image générée
     */
    suspend fun generateCharacterPortrait(
        characterName: String,
        physicalDescription: String,
        style: String = "realistic",
        gender: String = "female"
    ): Result<String> = withContext(Dispatchers.IO) {
        val detailedPrompt = buildCharacterPrompt(
            characterName,
            physicalDescription,
            style,
            gender
        )
        
        generateImage(
            prompt = detailedPrompt,
            width = 512,
            height = 768, // Portrait
            model = "flux", // Meilleure qualité pour portraits
            enhance = true
        )
    }
    
    /**
     * Génère plusieurs variations d'un personnage
     * Pour créer une galerie
     */
    suspend fun generateCharacterGallery(
        characterName: String,
        physicalDescription: String,
        style: String = "realistic",
        count: Int = 6
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val images = mutableListOf<String>()
            
            // Différentes poses/angles pour la galerie
            val variations = listOf(
                "front view, looking at camera",
                "side profile, elegant pose",
                "three quarter view, slight smile",
                "close-up portrait, detailed face",
                "full body shot, standing pose",
                "action pose, dynamic"
            )
            
            variations.take(count).forEach { variation ->
                val prompt = buildCharacterPrompt(
                    characterName,
                    physicalDescription,
                    style,
                    additionalDetails = variation
                )
                
                val result = generateImage(
                    prompt = prompt,
                    width = 512,
                    height = 768,
                    model = "flux"
                )
                
                result.getOrNull()?.let { images.add(it) }
                
                // Pause TRÈS longue pour ne pas surcharger l'API (augmenté à 8s)
                delay(8000)
            }
            
            if (images.isEmpty()) {
                Result.failure(IOException("Aucune image générée"))
            } else {
                Result.success(images)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Génère une vignette optimisée pour la sélection de personnage
     */
    suspend fun generateCharacterThumbnail(
        characterName: String,
        physicalDescription: String,
        style: String = "realistic"
    ): Result<String> = withContext(Dispatchers.IO) {
        val thumbnailPrompt = buildThumbnailPrompt(characterName, physicalDescription, style)
        
        generateImage(
            prompt = thumbnailPrompt,
            width = 400,
            height = 400, // Carré pour vignette
            model = "turbo", // Plus rapide pour vignettes
            enhance = true
        )
    }
    
    /**
     * Construit un prompt détaillé pour un personnage
     */
    private fun buildCharacterPrompt(
        name: String,
        physicalDescription: String,
        style: String,
        gender: String = "female",
        additionalDetails: String = ""
    ): String {
        val styleModifier = when (style.lowercase()) {
            "realistic" -> "photorealistic, ultra detailed, professional photography, natural lighting, 8k uhd"
            "anime" -> "anime style, manga art, detailed anime character, vibrant colors, anime aesthetic"
            "cinematic" -> "cinematic lighting, movie still, dramatic, film quality, professional"
            "artistic" -> "artistic portrait, oil painting style, detailed, beautiful composition"
            else -> "high quality, professional, detailed"
        }
        
        val genderDetails = when (gender.lowercase()) {
            "male" -> "handsome male, masculine features"
            "female" -> "beautiful woman, feminine features"
            else -> ""
        }
        
        return buildString {
            append("portrait of $name, ")
            append("$physicalDescription, ")
            if (genderDetails.isNotEmpty()) append("$genderDetails, ")
            if (additionalDetails.isNotEmpty()) append("$additionalDetails, ")
            append("$styleModifier, ")
            append("sharp focus, detailed face, expressive eyes, ")
            append("professional quality, masterpiece")
        }.trim()
    }
    
    /**
     * Construit un prompt optimisé pour vignette
     */
    private fun buildThumbnailPrompt(
        name: String,
        physicalDescription: String,
        style: String
    ): String {
        val stylePrefix = when (style.lowercase()) {
            "realistic" -> "photorealistic portrait"
            "anime" -> "anime character portrait"
            else -> "portrait"
        }
        
        return buildString {
            append("$stylePrefix, ")
            append("$name, ")
            append("$physicalDescription, ")
            append("headshot, centered, ")
            append("professional lighting, ")
            append("clean background, ")
            append("high quality, sharp focus")
        }.trim()
    }
    
    /**
     * Enrichit un prompt basique
     */
    private fun enhancePrompt(prompt: String): String {
        return buildString {
            append(prompt)
            if (!prompt.contains("quality")) {
                append(", high quality, detailed")
            }
            if (!prompt.contains("professional")) {
                append(", professional")
            }
            append(", masterpiece")
        }.trim()
    }
    
    /**
     * Génère une vidéo avec Pollination AI
     * @param prompt Description détaillée de la vidéo
     * @param width Largeur de la vidéo
     * @param height Hauteur de la vidéo
     * @param duration Durée en secondes (3-10s)
     * @param model Modèle à utiliser
     * @return URL directe de la vidéo générée (MP4)
     */
    suspend fun generateVideo(
        prompt: String,
        width: Int = 512,
        height: Int = 512,
        duration: Int = 5, // 5 secondes par défaut
        model: String = DEFAULT_VIDEO_MODEL,
        enhance: Boolean = true,
        isNSFW: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Délai minimal pour éviter spam (génération vidéo = plus lourd)
            delay(3000)
            
            // Retry avec backoff exponentiel
            var lastException: Exception? = null
            val maxRetries = 5
            
            for (attempt in 1..maxRetries) {
                try {
                    // Ajouter contexte NSFW si nécessaire
                    val enhancedPrompt = if (isNSFW) {
                        "adult content 18+, explicit, $prompt"
                    } else {
                        prompt
                    }
                    
                    // Encoder le prompt pour URL
                    val encodedPrompt = java.net.URLEncoder.encode(
                        if (enhance) enhanceVideoPrompt(enhancedPrompt) else enhancedPrompt,
                        "UTF-8"
                    )
                    
                    val videoUrl = buildString {
                        append(VIDEO_BASE_URL)
                        append("/")
                        append(encodedPrompt)
                        append("?width=$width")
                        append("&height=$height")
                        append("&model=$model")
                        append("&duration=$duration")
                        append("&nologo=true")
                        append("&enhance=true")
                        // Seed unique
                        append("&seed=${System.currentTimeMillis()}")
                    }
                    
                    // Vérifier que la vidéo est accessible
                    val request = Request.Builder()
                        .url(videoUrl)
                        .get()
                        .build()
                    
                    client.newCall(request).execute().use { response ->
                        when (response.code) {
                            200 -> {
                                val contentLength = response.body?.contentLength() ?: 0
                                if (contentLength > 10000) { // Au moins 10KB pour une vidéo
                                    return@withContext Result.success(videoUrl)
                                } else {
                                    throw IOException("Vidéo trop petite ou invalide")
                                }
                            }
                            429 -> {
                                if (attempt < maxRetries) {
                                    delay(30000L * attempt) // 30s, 60s, 90s... pour vidéos
                                    lastException = IOException("Rate limit 429 (tentative $attempt/$maxRetries)")
                                } else {
                                    return@withContext Result.failure(
                                        IOException("❌ Rate limit - Trop de requêtes vidéo. Réessayez dans 5 minutes.")
                                    )
                                }
                            }
                            500, 502, 503, 504 -> {
                                if (attempt < maxRetries) {
                                    delay(20000L * attempt) // 20s, 40s, 60s...
                                    lastException = IOException("Erreur serveur ${response.code} (tentative $attempt/$maxRetries)")
                                } else {
                                    return@withContext Result.failure(
                                        IOException("❌ Erreur ${response.code} - Service Pollinations AI surchargé. Réessayez plus tard.")
                                    )
                                }
                            }
                            else -> {
                                return@withContext Result.failure(
                                    IOException("Erreur génération vidéo: HTTP ${response.code}")
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < maxRetries) {
                        delay(5000L * attempt)
                    }
                }
            }
            
            Result.failure(lastException ?: IOException("Échec génération vidéo après $maxRetries tentatives"))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Enrichit un prompt vidéo
     */
    private fun enhanceVideoPrompt(prompt: String): String {
        return buildString {
            append(prompt)
            if (!prompt.contains("smooth")) {
                append(", smooth motion, cinematic")
            }
            if (!prompt.contains("quality")) {
                append(", high quality, detailed")
            }
            append(", professional video, masterpiece")
        }.trim()
    }
    
    /**
     * Teste l'API (toujours disponible, gratuite)
     */
    suspend fun ping(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Test simple avec un prompt basique
            val testUrl = "$BASE_URL/test?width=64&height=64&nologo=true"
            val request = Request.Builder()
                .url(testUrl)
                .head()
                .build()
            
            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Génère une scène basée sur un scénario
     * Utile pour illustrer le background story du personnage
     */
    suspend fun generateSceneImage(
        scenarioDescription: String,
        style: String = "cinematic"
    ): Result<String> = withContext(Dispatchers.IO) {
        val scenePrompt = buildScenePrompt(scenarioDescription, style)
        
        generateImage(
            prompt = scenePrompt,
            width = 768,
            height = 512, // Paysage pour scènes
            model = "flux",
            enhance = true
        )
    }
    
    /**
     * Construit un prompt pour une scène
     */
    private fun buildScenePrompt(description: String, style: String): String {
        val styleModifier = when (style.lowercase()) {
            "cinematic" -> "cinematic scene, movie still, dramatic lighting, film quality"
            "anime" -> "anime scene, manga style, detailed background, vibrant colors"
            "realistic" -> "photorealistic scene, natural lighting, detailed environment"
            else -> "detailed scene, high quality"
        }
        
        return "$description, $styleModifier, atmospheric, detailed, professional, masterpiece"
    }
}
