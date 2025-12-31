package com.narutoai.chat.utils

import com.narutoai.chat.models.Character
import com.narutoai.chat.models.CharacterCategory

/**
 * Normalise/compose des tags pour l'UI (recherche, filtres, chips).
 *
 * - Utilise d'abord les tags explicites dans `character.personality`.
 * - Ajoute des tags auto (genre/couleur cheveux/yeux/etc) via [AutoTagger].
 * - Pour les persos "Naruto" existants, on infère le genre via l'ID.
 */
object CharacterTagUtils {

    private val narutoMaleIds = setOf("naruto", "sasuke", "kakashi", "itachi")
    private val narutoFemaleIds = setOf("sakura", "hinata")

    fun uiTagsFor(character: Character): List<String> {
        val base = character.personality
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Si le personnage fournit déjà un tag de genre explicite, on respecte.
        val baseLower = base.map { it.lowercase() }
        val explicitGender: String? = when {
            baseLower.any { it == "non-binaire" || it == "non binaire" } -> "non-binaire"
            baseLower.any { it == "femme" } -> "femme"
            baseLower.any { it == "homme" } -> "homme"
            else -> null
        }

        val inferredGender = explicitGender ?: when (character.category) {
            CharacterCategory.CELEBRITY_FEMALE -> "femme"
            CharacterCategory.CELEBRITY_MALE -> "homme"
            CharacterCategory.NARUTO -> when {
                character.id in narutoFemaleIds -> "femme"
                character.id in narutoMaleIds -> "homme"
                else -> null
            }
        }

        val auto = AutoTagger.generateTags(
            gender = inferredGender,
            hairColor = character.hairColor,
            eyeColor = character.eyeColor,
            skinTone = null,
            bodyType = character.bodyType,
            age = character.age,
            height = character.height
        )

        return (base + auto)
            .map { it.lowercase().trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
}

