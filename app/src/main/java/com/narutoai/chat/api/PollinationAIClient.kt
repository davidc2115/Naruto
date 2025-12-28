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
        
        // Configuration par défaut
        private const val DEFAULT_WIDTH = 512
        private const val DEFAULT_HEIGHT = 768
        private const val DEFAULT_MODEL = "turbo" // Plus rapide
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
            // Delay LONG pour éviter rate limit 429 (augmenté à 5s)
            delay(5000)
            
            // Retry avec backoff exponentiel pour gérer 502 Bad Gateway
            var lastException: Exception? = null
            val maxRetries = 3
            
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
                    
                    // Vérifier que l'image est accessible avec timeout étendu
                    val request = Request.Builder()
                        .url(imageUrl)
                        .head() // Juste vérifier, pas télécharger
                        .build()
                    
                    client.newCall(request).execute().use { response ->
                        when (response.code) {
                            200 -> return@withContext Result.success(imageUrl)
                            429 -> {
                                // Rate limit - attendre plus longtemps
                                if (attempt < maxRetries) {
                                    delay(10000L * attempt) // 10s, 20s, 30s
                                    lastException = IOException("Rate limit 429 (tentative $attempt/$maxRetries)")
                                    // Continue to next retry
                                } else {
                                    return@withContext Result.failure(
                                        IOException("❌ Rate limit 429 - Trop de requêtes. Veuillez réessayer dans 1 minute.")
                                    )
                                }
                            }
                            500, 502, 503, 504 -> {
                                // Internal Server Error / Bad Gateway / Service Unavailable - retry
                                if (attempt < maxRetries) {
                                    delay(5000L * attempt) // 5s, 10s, 15s
                                    lastException = IOException("Erreur serveur ${response.code} (tentative $attempt/$maxRetries)")
                                    // Continue to next retry
                                } else {
                                    return@withContext Result.failure(
                                        IOException("❌ Erreur ${response.code} - Service Pollinations AI temporairement indisponible. Réessayez dans quelques minutes.")
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
