package com.narutoai.chat.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.narutoai.chat.data.PhysicalDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client pour Hugging Face Inference API - GRATUIT et ILLIMITÉ
 * Utilise des modèles open-source pour l'analyse d'images
 */
class HuggingFaceVisionClient(private val context: Context) {
    
    companion object {
        // Modèles vision gratuits sur Hugging Face
        private const val BLIP_MODEL = "Salesforce/blip-image-captioning-large" // Description générale
        private const val BLIP2_MODEL = "Salesforce/blip2-opt-2.7b" // Meilleure qualité
        private const val GIT_MODEL = "microsoft/git-large-coco" // Bon pour détails
        
        // API Hugging Face Inference (GRATUITE, pas de clé nécessaire!)
        private const val HF_API_BASE = "https://api-inference.huggingface.co/models"
    }
    
    /**
     * Analyser une image pour créer un descriptif physique détaillé
     * FALLBACK SIMPLE: Retourne un template à remplir manuellement
     */
    suspend fun analyzePhotoForCharacter(imageUri: Uri): Result<PhysicalDescription> = withContext(Dispatchers.IO) {
        try {
            // 1. Charger l'image pour vérifier qu'elle existe
            val bitmap = loadBitmapFromUri(imageUri)
                ?: return@withContext Result.failure(Exception("Impossible de charger l'image"))
            
            // 2. Analyser les dimensions et luminosité de base
            val width = bitmap.width
            val height = bitmap.height
            val aspectRatio = width.toFloat() / height.toFloat()
            
            // 3. Heuristiques simples basées sur l'aspect ratio
            val isPortrait = aspectRatio < 0.8f
            val isLandscape = aspectRatio > 1.2f
            
            // 4. Créer un template intelligent
            val analysis = PhysicalDescription(
                age = "25-35 ans",
                gender = if (isPortrait) "À définir" else "À définir",
                hairColor = "À définir (ex: Blond, Brun, Noir, Roux)",
                eyeColor = "À définir (ex: Bleu, Vert, Marron, Noisette)",
                skinTone = "À définir (ex: Claire, Mate, Bronzée, Foncée)",
                bodyType = "À définir (ex: Athlétique, Mince, Musclé, Courbes)",
                bustSize = "À définir si féminin (ex: Bonnet A, B, C, D)",
                penisSize = "À définir si masculin (ex: 16cm, 18cm, 20cm)",
                height = "À définir (ex: 160cm, 170cm, 180cm)",
                facialFeatures = "À définir (ex: Traits fins, Mâchoire carrée, Visage rond)",
                distinctiveFeatures = "À définir (ex: Tatouages, Piercings, Cicatrices)",
                detailedDescription = """✅ Image chargée avec succès !
                    
📐 Dimensions: ${width}x${height}px
🖼️ Format: ${if (isPortrait) "Portrait" else if (isLandscape) "Paysage" else "Carré"}

⚠️ ANALYSE AUTOMATIQUE NON DISPONIBLE
Les API gratuites d'analyse d'image ne sont plus accessibles sans clé.

👉 Veuillez REMPLIR MANUELLEMENT les champs ci-dessus :
- Genre (Homme/Femme/Non-binaire)
- Âge approximatif
- Couleur cheveux et yeux
- Type de corps
- Taille de poitrine (si féminin)
- Taille du sexe (si masculin)
- Description détaillée physique

💡 Astuce: Regardez bien votre photo et décrivez ce que vous voyez !"""
            )
            
            Result.success(analysis)
            
        } catch (e: Exception) {
            Result.failure(Exception("Erreur chargement image: ${e.message}"))
        }
    }
    
    /**
     * Requête vers Hugging Face Inference API
     * GRATUIT et sans clé API !
     */
    private suspend fun queryHuggingFaceVision(model: String, base64Image: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("$HF_API_BASE/$model")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
            }
            
            // Payload JSON avec image en base64
            val payload = JSONObject().apply {
                put("inputs", base64Image)
                put("options", JSONObject().apply {
                    put("wait_for_model", true)
                })
            }
            
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Erreur HTTP $responseCode"
                throw Exception("Erreur API: $error")
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            
            // Parser la réponse (peut être array ou object selon modèle)
            try {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val firstResult = jsonArray.getJSONObject(0)
                    return@withContext firstResult.optString("generated_text", "")
                }
            } catch (e: Exception) {
                // Si pas un array, essayer comme object
                try {
                    val jsonObject = JSONObject(response)
                    return@withContext jsonObject.optString("generated_text", "")
                } catch (e2: Exception) {
                    // Retourner brut
                    return@withContext response
                }
            }
            
            ""
        } catch (e: Exception) {
            throw Exception("Erreur requête HuggingFace: ${e.message}")
        }
    }
    
    /**
     * Parser la description pour extraire infos physiques
     */
    private fun parsePhysicalDescription(description: String): PhysicalDescription {
        val lowerDesc = description.lowercase()
        
        // Détecter genre
        val gender = when {
            lowerDesc.contains("woman") || lowerDesc.contains("female") || lowerDesc.contains("girl") -> "Femme"
            lowerDesc.contains("man") || lowerDesc.contains("male") || lowerDesc.contains("boy") -> "Homme"
            else -> "Personne"
        }
        
        // Détecter âge approximatif
        val age = when {
            lowerDesc.contains("young") || lowerDesc.contains("teen") -> "18-25 ans"
            lowerDesc.contains("middle-aged") || lowerDesc.contains("adult") -> "30-40 ans"
            lowerDesc.contains("old") || lowerDesc.contains("elderly") -> "50+ ans"
            else -> "25-35 ans"
        }
        
        // Détecter couleur cheveux
        val hairColor = when {
            lowerDesc.contains("blonde") || lowerDesc.contains("blond") -> "Blond"
            lowerDesc.contains("brown hair") || lowerDesc.contains("brunette") -> "Châtain/Brun"
            lowerDesc.contains("black hair") -> "Noir"
            lowerDesc.contains("red hair") || lowerDesc.contains("ginger") -> "Roux"
            lowerDesc.contains("gray") || lowerDesc.contains("grey") -> "Gris"
            else -> "Cheveux naturels"
        }
        
        // Détecter couleur yeux
        val eyeColor = when {
            lowerDesc.contains("blue eyes") -> "Yeux bleus"
            lowerDesc.contains("brown eyes") -> "Yeux marrons"
            lowerDesc.contains("green eyes") -> "Yeux verts"
            lowerDesc.contains("hazel") -> "Yeux noisette"
            else -> "Yeux expressifs"
        }
        
        // Détecter type de corps
        val bodyType = when {
            lowerDesc.contains("slim") || lowerDesc.contains("slender") -> "Mince et élancé(e)"
            lowerDesc.contains("athletic") || lowerDesc.contains("fit") -> "Athlétique et tonique"
            lowerDesc.contains("curvy") || lowerDesc.contains("voluptuous") -> "Courbes généreuses"
            lowerDesc.contains("muscular") -> "Musclé(e) et puissant(e)"
            else -> "Silhouette équilibrée"
        }
        
        // Détecter taille de poitrine (si féminin)
        val bustSize = if (gender == "Femme") {
            when {
                lowerDesc.contains("large breast") || lowerDesc.contains("big breast") -> "Poitrine généreuse (Bonnet D+)"
                lowerDesc.contains("medium breast") || lowerDesc.contains("average breast") -> "Poitrine moyenne (Bonnet C)"
                lowerDesc.contains("small breast") || lowerDesc.contains("petite breast") -> "Petite poitrine (Bonnet A-B)"
                lowerDesc.contains("curvy") || lowerDesc.contains("voluptuous") -> "Poitrine généreuse (Bonnet D)"
                lowerDesc.contains("slim") || lowerDesc.contains("slender") -> "Poitrine petite (Bonnet A)"
                else -> "Poitrine moyenne (Bonnet B-C)"
            }
        } else {
            ""
        }
        
        // Détecter taille du sexe (si masculin)
        val penisSize = if (gender == "Homme") {
            when {
                lowerDesc.contains("well-endowed") || lowerDesc.contains("muscular") -> "Bien membré (20cm)"
                lowerDesc.contains("athletic") || lowerDesc.contains("fit") -> "Taille généreuse (18cm)"
                else -> "Taille moyenne (16cm)"
            }
        } else {
            ""
        }
        
        // Construire description détaillée
        val detailedDescription = buildString {
            append("$gender de $age, ")
            append("$hairColor, $eyeColor. ")
            append("$bodyType. ")
            append("\n\nDescription IA complète:\n$description")
        }
        
        return PhysicalDescription(
            age = age,
            gender = gender,
            hairColor = hairColor,
            eyeColor = eyeColor,
            skinTone = "Peau naturelle",
            bodyType = bodyType,
            bustSize = bustSize,
            penisSize = penisSize,
            height = "Taille moyenne",
            facialFeatures = "Traits harmonieux",
            distinctiveFeatures = "Voir description détaillée",
            detailedDescription = detailedDescription
        )
    }
    
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Redimensionner pour optimiser (max 512px)
        val maxSize = 512
        val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaledBitmap = if (ratio < 1.0f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else {
            bitmap
        }
        
        val byteArrayOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}

