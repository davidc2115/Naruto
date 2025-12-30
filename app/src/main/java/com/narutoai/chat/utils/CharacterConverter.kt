package com.narutoai.chat.utils

import com.narutoai.chat.data.CustomCharacterEntity
import com.narutoai.chat.models.Character
import com.narutoai.chat.models.CharacterCategory
import org.json.JSONArray

/**
 * Convertit un CustomCharacterEntity en Character pour le chat
 */
object CharacterConverter {
    
    fun toCharacter(entity: CustomCharacterEntity): Character {
        return Character(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            category = CharacterCategory.CELEBRITY_MALE, // Catégorie par défaut pour custom
            systemPromptSFW = entity.systemPromptSFW,
            systemPromptNSFW = entity.systemPromptNSFW,
            avatarEmoji = "👤", // Emoji par défaut
            personality = parseJsonArray(entity.personality),
            imageUrl = "",
            imageResId = 0,
            
            // Détails physiques
            physicalDescription = entity.physicalDescription,
            age = entity.age,
            height = entity.height,
            hairColor = entity.hairColor,
            eyeColor = entity.eyeColor,
            bodyType = entity.bodyType,
            distinctiveFeatures = parseJsonArray(entity.distinctiveFeatures),
            
            // Background
            scenario = entity.scenario,
            backgroundStory = entity.backgroundStory,
            
            // Tempérament
            temperament = entity.temperament,
            characterTraits = parseJsonArray(entity.characterTraits),
            likes = parseJsonArray(entity.likes),
            dislikes = parseJsonArray(entity.dislikes),
            skills = parseJsonArray(entity.skills),
            
            // Galeries (vides pour custom pour l'instant)
            gallery = emptyList(),
            galleryNSFW = emptyList(),
            thumbnailUrl = "",
            
            // Message d'accueil
            greetingMessage = entity.greetingMessage
        )
    }
    
    /**
     * Parse une string JSON en List<String>
     */
    private fun parseJsonArray(jsonString: String): List<String> {
        return try {
            if (jsonString.isEmpty() || jsonString == "[]") {
                emptyList()
            } else {
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getString(i))
                }
                list
            }
        } catch (e: Exception) {
            android.util.Log.e("CharacterConverter", "Error parsing JSON array: $jsonString", e)
            emptyList()
        }
    }
    
    /**
     * Convertit un Character en CustomCharacterEntity pour sauvegarde
     */
    fun toEntity(character: Character, avatarImagePath: String = ""): CustomCharacterEntity {
        return CustomCharacterEntity(
            id = character.id,
            name = character.name,
            description = character.description,
            systemPromptSFW = character.systemPromptSFW,
            systemPromptNSFW = character.systemPromptNSFW,
            avatarImagePath = avatarImagePath,
            personality = toJsonArray(character.personality),
            physicalDescription = character.physicalDescription,
            age = character.age,
            height = character.height,
            hairColor = character.hairColor,
            eyeColor = character.eyeColor,
            bodyType = character.bodyType,
            distinctiveFeatures = toJsonArray(character.distinctiveFeatures),
            scenario = character.scenario,
            backgroundStory = character.backgroundStory,
            temperament = character.temperament,
            characterTraits = toJsonArray(character.characterTraits),
            likes = toJsonArray(character.likes),
            dislikes = toJsonArray(character.dislikes),
            skills = toJsonArray(character.skills),
            greetingMessage = character.greetingMessage
        )
    }
    
    /**
     * Convertit List<String> en JSON array string
     */
    private fun toJsonArray(list: List<String>): String {
        return if (list.isEmpty()) {
            "[]"
        } else {
            JSONArray(list).toString()
        }
    }
}
