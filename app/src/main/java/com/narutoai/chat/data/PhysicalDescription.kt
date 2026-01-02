package com.narutoai.chat.data

/**
 * Data class pour stocker une description physique d'un personnage
 * Utilisée pour l'analyse automatique par IA et la création de personnages
 */
data class PhysicalDescription(
    val age: String = "",
    val gender: String = "",
    val hairColor: String = "",
    val eyeColor: String = "",
    val skinTone: String = "",
    val bodyType: String = "",
    val height: String = "",
    val facialFeatures: String = "",
    val distinctiveFeatures: String = "",
    val detailedDescription: String = ""
) {
    /**
     * Convertit la description en texte formaté pour affichage
     */
    fun toFormattedDescription(): String {
        val parts = mutableListOf<String>()
        
        if (detailedDescription.isNotBlank()) {
            parts.add(detailedDescription)
        }
        
        val details = mutableListOf<String>()
        if (age.isNotBlank()) details.add("Âge: $age")
        if (gender.isNotBlank()) details.add("Genre: $gender")
        if (hairColor.isNotBlank()) details.add("Cheveux: $hairColor")
        if (eyeColor.isNotBlank()) details.add("Yeux: $eyeColor")
        if (skinTone.isNotBlank()) details.add("Teint: $skinTone")
        if (bodyType.isNotBlank()) details.add("Morphologie: $bodyType")
        if (height.isNotBlank()) details.add("Taille: $height")
        if (facialFeatures.isNotBlank()) details.add("Visage: $facialFeatures")
        if (distinctiveFeatures.isNotBlank()) details.add("Signes distinctifs: $distinctiveFeatures")
        
        if (details.isNotEmpty()) {
            parts.add("\n" + details.joinToString("\n"))
        }
        
        return parts.joinToString("\n\n")
    }
}
