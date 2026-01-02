package com.narutoai.chat.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Client pour Hugging Face Inference API (analyse d'images)
 * 100% GRATUIT, SANS CLÉ API, et ILLIMITÉ
 * 
 * Utilise des modèles publics accessibles sans authentification :
 * - Salesforce/blip-image-captioning-large (description d'images)
 * - nlpconnect/vit-gpt2-image-captioning (alternative)
 * 
 * Avantages :
 * ✅ Aucune clé API requise
 * ✅ Gratuit et illimité (rate limit raisonnable)
 * ✅ Modèles open-source de qualité
 * ✅ Hébergé par Hugging Face (fiable)
 * 
 * https://huggingface.co/docs/api-inference/
 */
class HuggingFaceVisionClient(private val context: Context) {
    
    companion object {
        // API Hugging Face Inference (PUBLIQUE, pas de clé requise!)
        private const val HF_API_BASE = "https://api-inference.huggingface.co/models"
        
        // Modèles publics pour vision (sans authentification)
        private val VISION_MODELS = listOf(
            "Salesforce/blip-image-captioning-large",     // Meilleur pour descriptions détaillées
            "nlpconnect/vit-gpt2-image-captioning",       // Alternative rapide
            "Salesforce/blip2-opt-2.7b"                   // Plus puissant (peut être lent)
        )
        
        private const val MAX_IMAGE_SIZE_KB = 1024 // 1MB max pour HuggingFace
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Analyse une image et génère un descriptif physique détaillé
     * GRATUIT, SANS CLÉ, ILLIMITÉ
     */
    suspend fun analyzePhotoForCharacter(imageUri: Uri): Result<PhysicalDescription> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("HuggingFaceVision", "🎨 Démarrage analyse avec Hugging Face")
                
                // Charger et compresser l'image
                val imageBytes = loadAndCompressImage(imageUri)
                    ?: return@withContext Result.failure(Exception("Impossible de charger l'image"))
                
                android.util.Log.d("HuggingFaceVision", "📷 Image encodée (${imageBytes.size / 1024}KB)")
                
                // Essayer chaque modèle avec fallback
                var lastException: Exception? = null
                
                for ((index, model) in VISION_MODELS.withIndex()) {
                    try {
                        android.util.Log.d("HuggingFaceVision", "🔄 Tentative modèle ${index + 1}/${VISION_MODELS.size}: $model")
                        
                        val description = analyzeWithModel(model, imageBytes)
                        
                        if (description != null) {
                            android.util.Log.d("HuggingFaceVision", "✅ Analyse réussie avec $model")
                            return@withContext Result.success(description)
                        }
                        
                    } catch (e: Exception) {
                        android.util.Log.w("HuggingFaceVision", "⚠️ Échec $model: ${e.message}")
                        lastException = e
                        
                        // Si modèle en cours de chargement, attendre et réessayer
                        if (e.message?.contains("loading") == true && index < VISION_MODELS.size - 1) {
                            kotlinx.coroutines.delay(5000) // Attendre 5s
                            continue
                        }
                    }
                }
                
                // Tous les modèles ont échoué
                Result.failure(
                    lastException ?: Exception("Aucun modèle disponible. Réessayez dans quelques secondes.")
                )
                
            } catch (e: Exception) {
                android.util.Log.e("HuggingFaceVision", "❌ Erreur analyse: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Analyse avec un modèle spécifique
     */
    private suspend fun analyzeWithModel(model: String, imageBytes: ByteArray): PhysicalDescription? {
        return withContext(Dispatchers.IO) {
            try {
                // URL du modèle
                val url = "$HF_API_BASE/$model"
                
                // Créer la requête (envoi direct des bytes image)
                val requestBody = imageBytes.toRequestBody("application/octet-stream".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/octet-stream")
                    .post(requestBody)
                    .build()
                
                // Exécuter la requête
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    android.util.Log.e("HuggingFaceVision", "❌ HTTP ${response.code}: $responseBody")
                    
                    // Parser l'erreur pour informations utiles
                    if (responseBody?.contains("loading") == true) {
                        throw Exception("Modèle en cours de chargement, réessayez dans 5 secondes")
                    }
                    
                    throw Exception("HTTP ${response.code}")
                }
                
                if (responseBody == null) {
                    throw Exception("Réponse vide")
                }
                
                android.util.Log.d("HuggingFaceVision", "📝 Réponse: ${responseBody.take(200)}...")
                
                // Parser la réponse
                val description = parseHuggingFaceResponse(responseBody)
                
                if (description != null) {
                    android.util.Log.d("HuggingFaceVision", "✅ Description extraite")
                    description
                } else {
                    throw Exception("Impossible de parser la réponse")
                }
                
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    /**
     * Parse la réponse de Hugging Face
     * Format: [{"generated_text": "a photo of..."}] ou {"generated_text": "..."}
     */
    private fun parseHuggingFaceResponse(responseBody: String): PhysicalDescription? {
        try {
            // Essayer format array
            val jsonArray = try {
                JSONArray(responseBody)
            } catch (e: Exception) {
                // Essayer format object
                val jsonObject = JSONObject(responseBody)
                val array = JSONArray()
                array.put(jsonObject)
                array
            }
            
            if (jsonArray.length() == 0) {
                return null
            }
            
            // Extraire la description générée
            val firstResult = jsonArray.getJSONObject(0)
            val generatedText = firstResult.optString("generated_text", "")
                .ifEmpty { firstResult.optString("caption", "") }
                .ifEmpty { firstResult.optString("text", "") }
            
            if (generatedText.isEmpty()) {
                return null
            }
            
            android.util.Log.d("HuggingFaceVision", "📄 Texte généré: $generatedText")
            
            // Analyser le texte pour extraire les informations
            return extractPhysicalDetailsFromText(generatedText)
            
        } catch (e: Exception) {
            android.util.Log.e("HuggingFaceVision", "Erreur parsing: ${e.message}")
            return null
        }
    }
    
    /**
     * Extrait les détails physiques d'un texte descriptif
     * Utilise des heuristiques simples pour deviner les caractéristiques
     */
    private fun extractPhysicalDetailsFromText(text: String): PhysicalDescription {
        val lowerText = text.lowercase()
        
        // Deviner le genre
        val gender = when {
            lowerText.contains("woman") || lowerText.contains("girl") || lowerText.contains("female") -> "femme"
            lowerText.contains("man") || lowerText.contains("boy") || lowerText.contains("male") -> "homme"
            else -> ""
        }
        
        // Deviner l'âge
        val age = when {
            lowerText.contains("young") || lowerText.contains("teen") -> "jeune (18-25 ans)"
            lowerText.contains("old") || lowerText.contains("elder") -> "mature (50+ ans)"
            lowerText.contains("middle") -> "adulte (30-45 ans)"
            else -> "adulte"
        }
        
        // Deviner cheveux
        val hairColor = when {
            lowerText.contains("blonde") || lowerText.contains("blond hair") -> "blonds"
            lowerText.contains("brown hair") || lowerText.contains("brunette") -> "bruns"
            lowerText.contains("black hair") || lowerText.contains("dark hair") -> "noirs"
            lowerText.contains("red hair") || lowerText.contains("ginger") -> "roux"
            lowerText.contains("gray") || lowerText.contains("grey") -> "gris"
            lowerText.contains("white hair") -> "blancs"
            lowerText.contains("long hair") -> "longs"
            lowerText.contains("short hair") -> "courts"
            else -> ""
        }
        
        // Deviner yeux
        val eyeColor = when {
            lowerText.contains("blue eyes") -> "bleus"
            lowerText.contains("brown eyes") -> "marron"
            lowerText.contains("green eyes") -> "verts"
            lowerText.contains("hazel") -> "noisette"
            else -> ""
        }
        
        // Deviner teint
        val skinTone = when {
            lowerText.contains("fair skin") || lowerText.contains("pale") -> "clair"
            lowerText.contains("tan") || lowerText.contains("olive") -> "mat"
            lowerText.contains("dark skin") || lowerText.contains("black") -> "foncé"
            else -> ""
        }
        
        // Deviner morphologie
        val bodyType = when {
            lowerText.contains("thin") || lowerText.contains("slim") || lowerText.contains("skinny") -> "mince"
            lowerText.contains("athletic") || lowerText.contains("fit") -> "athlétique"
            lowerText.contains("muscular") || lowerText.contains("strong") -> "musclé"
            lowerText.contains("curvy") || lowerText.contains("voluptuous") -> "voluptueux"
            lowerText.contains("large") || lowerText.contains("heavy") -> "corpulent"
            else -> "moyen"
        }
        
        // Deviner taille
        val height = when {
            lowerText.contains("tall") -> "grande (~175-185cm)"
            lowerText.contains("short") -> "petite (~155-165cm)"
            else -> "moyenne (~165-175cm)"
        }
        
        // Signes distinctifs
        val distinctiveFeatures = buildString {
            if (lowerText.contains("tattoo")) append("tatouage, ")
            if (lowerText.contains("glasses") || lowerText.contains("spectacles")) append("lunettes, ")
            if (lowerText.contains("beard")) append("barbe, ")
            if (lowerText.contains("mustache")) append("moustache, ")
            if (lowerText.contains("scar")) append("cicatrice, ")
            if (lowerText.contains("piercing")) append("piercing, ")
        }.removeSuffix(", ")
        
        // Traits du visage
        val facialFeatures = when {
            lowerText.contains("smile") || lowerText.contains("smiling") -> "souriant, expression agréable"
            lowerText.contains("serious") -> "expression sérieuse"
            lowerText.contains("beautiful") || lowerText.contains("attractive") -> "traits harmonieux"
            lowerText.contains("cute") -> "traits doux"
            else -> "traits réguliers"
        }
        
        // Description détaillée = texte original nettoyé
        val detailedDescription = text
            .replace("a photo of ", "")
            .replace("an image of ", "")
            .trim()
            .capitalize()
        
        return PhysicalDescription(
            age = age,
            gender = gender,
            hairColor = hairColor.ifEmpty { "non spécifié" },
            eyeColor = eyeColor.ifEmpty { "non spécifié" },
            skinTone = skinTone.ifEmpty { "non spécifié" },
            bodyType = bodyType,
            height = height,
            facialFeatures = facialFeatures,
            distinctiveFeatures = distinctiveFeatures.ifEmpty { "aucun visible" },
            detailedDescription = detailedDescription
        )
    }
    
    /**
     * Charge et compresse une image en bytes
     */
    private fun loadAndCompressImage(uri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            // Redimensionner si trop grande
            val maxDimension = 1024
            if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val newWidth: Int
                val newHeight: Int
                
                if (ratio > 1) {
                    newWidth = maxDimension
                    newHeight = (maxDimension / ratio).toInt()
                } else {
                    newHeight = maxDimension
                    newWidth = (maxDimension * ratio).toInt()
                }
                
                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            }
            
            // Compresser en JPEG
            val outputStream = ByteArrayOutputStream()
            var quality = 85
            
            do {
                outputStream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                quality -= 10
            } while (outputStream.size() > MAX_IMAGE_SIZE_KB * 1024 && quality > 30)
            
            val imageBytes = outputStream.toByteArray()
            outputStream.close()
            
            android.util.Log.d("HuggingFaceVision", "📦 Image compressée: ${imageBytes.size / 1024}KB, qualité: $quality")
            
            imageBytes
            
        } catch (e: Exception) {
            android.util.Log.e("HuggingFaceVision", "❌ Erreur compression: ${e.message}", e)
            null
        }
    }
}
