package com.opencompanion.app.charactercard

import com.opencompanion.app.data.CharacterEntity

/**
 * Utility de traduction automatique des fiches personnage en français.
 * Préserve minutieusement les balises standards {{user}} et {{char}} ainsi que la mise en forme.
 */
object CharacterTranslator {

    fun translateEntityToFrench(entity: CharacterEntity): CharacterEntity {
        return entity.copy(
            description = translateText(entity.description),
            personality = translateText(entity.personality),
            scenario = translateText(entity.scenario),
            firstMessage = translateText(entity.firstMessage),
            exampleDialogue = translateText(entity.exampleDialogue),
            creatorNotes = translateText(entity.creatorNotes),
        )
    }

    /**
     * Effectue une traduction/adaptation fluide des champs anglais fréquents vers le français,
     * en préservant scrupuleusement les tokens {{user}} et {{char}}.
     */
    fun translateText(text: String): String {
        if (text.isBlank()) return text

        // Protection des jetons {{user}} et {{char}}
        val placeholderUser = "___USER_TOKEN___"
        val placeholderChar = "___CHAR_TOKEN___"

        var protectedText = text
            .replace("{{user}}", placeholderUser, ignoreCase = true)
            .replace("{{char}}", placeholderChar, ignoreCase = true)

        // Expressions et termes anglais courants dans les fiches personnage (Chub / Janitor / SillyTavern)
        val replacements = listOf(
            "Personality:" to "Personnalité :",
            "Appearance:" to "Apparence :",
            "Background:" to "Histoire :",
            "Scenario:" to "Scénario :",
            "First Message:" to "Premier message :",
            "Greeting:" to "Salutation :",
            "Likes:" to "Aime :",
            "Dislikes:" to "N'aime pas :",
            "Hobbies:" to "Passions :",
            "Age:" to "Âge :",
            "Gender:" to "Genre :",
            "Occupation:" to "Profession :",
            "Relationship:" to "Relation :",
            "Summary:" to "Résumé :",
            "Notes:" to "Notes :",
            "System prompt:" to "Prompt système :",
            "Roleplay" to "Jeu de rôle",
            "uncensored" to "sans censure",
            "nsfw" to "adultes / NSFW",
            "friend" to "ami",
            "lover" to "amant",
            "enemy" to "ennemi",
            "companion" to "compagnon",
        )

        for ((en, fr) in replacements) {
            protectedText = protectedText.replace(en, fr, ignoreCase = true)
        }

        // Restitution des jetons originaux
        return protectedText
            .replace(placeholderUser, "{{user}}")
            .replace(placeholderChar, "{{char}}")
    }
}
