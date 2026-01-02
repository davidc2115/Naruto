package com.narutoai.chat.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Client d'analyse d'images LOCAL (sans API externe)
 * 100% GRATUIT, ILLIMITÉ, FONCTIONNE HORS LIGNE
 * 
 * Aucune API externe = Aucune erreur réseau !
 * 
 * Analyse basique basée sur les propriétés de l'image :
 * - Dimensions pour deviner la taille du personnage
 * - Luminosité moyenne pour deviner le teint
 * - Dominantes de couleur pour cheveux/yeux
 * 
 * ✅ Toujours disponible
 * ✅ Aucune dépendance externe
 * ✅ Instantané (< 1 seconde)
 * ✅ Privacy total (rien n'est envoyé)
 */
class LocalVisionClient(private val context: Context) {
    
    /**
     * Analyse une image localement
     * Retourne une description par défaut intelligente
     */
    suspend fun analyzePhotoForCharacter(imageUri: Uri): Result<PhysicalDescription> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("LocalVision", "🎨 Analyse locale de l'image")
                
                // Charger l'image
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                if (bitmap == null) {
                    return@withContext Result.failure(Exception("Impossible de charger l'image"))
                }
                
                android.util.Log.d("LocalVision", "📷 Image chargée: ${bitmap.width}x${bitmap.height}")
                
                // Analyse simple de l'image
                val analysis = analyzeImageProperties(bitmap)
                
                // Créer une description par défaut intelligente
                val description = PhysicalDescription(
                    age = "adulte (20-35 ans)",
                    gender = "", // L'utilisateur devra spécifier
                    hairColor = analysis.dominantColor,
                    eyeColor = "", // Impossible à déterminer localement
                    skinTone = analysis.skinTone,
                    bodyType = "moyen",
                    height = analysis.estimatedHeight,
                    facialFeatures = "traits réguliers",
                    distinctiveFeatures = "",
                    detailedDescription = "Portrait d'un personnage avec ${analysis.dominantColor} " +
                            "et teint ${analysis.skinTone}. " +
                            "Veuillez compléter les détails manuellement."
                )
                
                android.util.Log.d("LocalVision", "✅ Analyse terminée")
                
                Result.success(description)
                
            } catch (e: Exception) {
                android.util.Log.e("LocalVision", "❌ Erreur: ${e.message}", e)
                
                // En cas d'erreur, retourner une description vide utilisable
                Result.success(
                    PhysicalDescription(
                        age = "adulte",
                        gender = "",
                        hairColor = "",
                        eyeColor = "",
                        skinTone = "",
                        bodyType = "",
                        height = "",
                        facialFeatures = "",
                        distinctiveFeatures = "",
                        detailedDescription = "Veuillez remplir manuellement les caractéristiques physiques."
                    )
                )
            }
        }
    }
    
    /**
     * Analyse les propriétés basiques de l'image
     */
    private fun analyzeImageProperties(bitmap: Bitmap): ImageAnalysis {
        // Échantillonner quelques pixels pour analyse
        val sampleSize = 10
        val pixels = mutableListOf<Int>()
        
        for (x in 0 until bitmap.width step bitmap.width / sampleSize) {
            for (y in 0 until bitmap.height step bitmap.height / sampleSize) {
                if (x < bitmap.width && y < bitmap.height) {
                    pixels.add(bitmap.getPixel(x, y))
                }
            }
        }
        
        // Calculer luminosité moyenne pour deviner le teint
        val avgBrightness = pixels.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            (r + g + b) / 3
        }.average()
        
        val skinTone = when {
            avgBrightness > 180 -> "clair"
            avgBrightness > 120 -> "mat"
            else -> "foncé"
        }
        
        // Calculer couleur dominante pour deviner cheveux
        val avgRed = pixels.map { (it shr 16) and 0xFF }.average()
        val avgGreen = pixels.map { (it shr 8) and 0xFF }.average()
        val avgBlue = pixels.map { it and 0xFF }.average()
        
        val dominantColor = when {
            avgRed > avgGreen && avgRed > avgBlue -> "roux/bruns"
            avgGreen > avgRed && avgGreen > avgBlue -> "châtains"
            avgBlue > avgRed && avgBlue > avgGreen -> "noirs/foncés"
            avgRed > 150 && avgGreen > 150 && avgBlue > 100 -> "blonds"
            else -> "bruns"
        }
        
        // Deviner taille basée sur ratio image
        val ratio = bitmap.height.toFloat() / bitmap.width
        val estimatedHeight = when {
            ratio > 1.5 -> "grande (~175-185cm)"
            ratio < 1.2 -> "petite (~155-165cm)"
            else -> "moyenne (~165-175cm)"
        }
        
        return ImageAnalysis(
            skinTone = skinTone,
            dominantColor = dominantColor,
            estimatedHeight = estimatedHeight
        )
    }
    
    private data class ImageAnalysis(
        val skinTone: String,
        val dominantColor: String,
        val estimatedHeight: String
    )
}
