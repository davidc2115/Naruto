package com.narutoai.chat.utils

/**
 * Génération simple de tags "type roleplay chatbot" à partir des champs détectés/saisis.
 * Objectif: produire des tags courts et cohérents (homme, femme, brune, blond, etc.).
 */
object AutoTagger {
    fun generateTags(
        gender: String?,
        hairColor: String?,
        eyeColor: String?,
        skinTone: String?,
        bodyType: String?,
        age: String?,
        height: String?
    ): List<String> {
        val tags = linkedSetOf<String>()

        val genderNorm = gender.orEmpty().lowercase()
        when {
            genderNorm.contains("femme") -> tags.add("femme")
            genderNorm.contains("homme") -> tags.add("homme")
            genderNorm.contains("girl") -> tags.add("femme")
            genderNorm.contains("woman") -> tags.add("femme")
            genderNorm.contains("man") -> tags.add("homme")
            genderNorm.contains("autre") || genderNorm.contains("non-binaire") || genderNorm.contains("non binaire") ->
                tags.add("non-binaire")
        }

        val hairNorm = hairColor.orEmpty().lowercase()
        val hairBase = when {
            hairNorm.contains("blond") -> "blond"
            hairNorm.contains("brun") -> "brun"
            hairNorm.contains("noir") -> "noir"
            hairNorm.contains("roux") -> "roux"
            hairNorm.contains("gris") || hairNorm.contains("grison") -> "gris"
            hairNorm.contains("blanc") -> "blanc"
            hairNorm.contains("rose") -> "rose"
            hairNorm.contains("bleu") -> "bleu"
            hairNorm.contains("vert") -> "vert"
            hairNorm.contains("violet") || hairNorm.contains("purple") -> "violet"
            else -> ""
        }
        if (hairBase.isNotEmpty()) {
            // Petite adaptation: "brune"/"blonde"/etc si genre femme détecté
            val feminine = tags.contains("femme")
            val hairTag = when (hairBase) {
                "blond" -> if (feminine) "blonde" else "blond"
                "brun" -> if (feminine) "brune" else "brun"
                "noir" -> if (feminine) "noire" else "noir"
                "roux" -> if (feminine) "rousse" else "roux"
                "gris" -> if (feminine) "grise" else "gris"
                "blanc" -> if (feminine) "blanche" else "blanc"
                else -> hairBase
            }
            tags.add(hairTag)
        }
        if (hairNorm.contains("long")) tags.add("cheveux longs")
        if (hairNorm.contains("court")) tags.add("cheveux courts")
        if (hairNorm.contains("boucl")) tags.add("cheveux bouclés")
        if (hairNorm.contains("lisse")) tags.add("cheveux lisses")

        val eyesNorm = eyeColor.orEmpty().lowercase()
        when {
            eyesNorm.contains("bleu") -> tags.add("yeux bleus")
            eyesNorm.contains("vert") -> tags.add("yeux verts")
            eyesNorm.contains("marron") || eyesNorm.contains("brun") -> tags.add("yeux marron")
            eyesNorm.contains("noir") -> tags.add("yeux noirs")
            eyesNorm.contains("gris") -> tags.add("yeux gris")
            eyesNorm.contains("hazel") || eyesNorm.contains("noisette") -> tags.add("yeux noisette")
        }

        val skinNorm = skinTone.orEmpty().lowercase()
        when {
            skinNorm.contains("clair") -> tags.add("peau claire")
            skinNorm.contains("mat") -> tags.add("peau mate")
            skinNorm.contains("fonc") -> tags.add("peau foncée")
            skinNorm.contains("pâl") || skinNorm.contains("pale") -> tags.add("peau pâle")
        }

        val bodyNorm = bodyType.orEmpty().lowercase()
        when {
            bodyNorm.contains("athl") -> tags.add("athlétique")
            bodyNorm.contains("minc") -> tags.add("mince")
            bodyNorm.contains("muscl") -> tags.add("musclé")
            bodyNorm.contains("pulpeus") || bodyNorm.contains("curvy") -> tags.add("pulpeuse")
        }

        val ageNorm = age.orEmpty().lowercase()
        when {
            ageNorm.contains("ado") -> tags.add("adolescent(e)")
            ageNorm.contains("jeune") -> tags.add("jeune adulte")
            ageNorm.contains("mature") -> tags.add("mature")
        }

        val heightNorm = height.orEmpty().lowercase()
        when {
            heightNorm.contains("petit") || heightNorm.contains("160") -> tags.add("petite taille")
            heightNorm.contains("moyen") || heightNorm.contains("170") -> tags.add("taille moyenne")
            heightNorm.contains("grand") || heightNorm.contains("180") -> tags.add("grande taille")
        }

        // Nettoyage: limiter aux tags non vides, uniques, et pas trop longs
        return tags
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(12)
    }
}

