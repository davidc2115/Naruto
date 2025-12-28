package com.narutoai.chat.models

data class Character(
    val id: String,
    val name: String,
    val description: String,
    val category: CharacterCategory,
    val systemPromptSFW: String,
    val systemPromptNSFW: String,
    val avatarEmoji: String,
    val personality: List<String>,
    val imageUrl: String = "", // URL de l'image (non utilisée)
    val imageResId: Int = 0, // Ressource drawable locale
    
    // NOUVEAUX CHAMPS pour présentation détaillée
    val physicalDescription: String = "", // Description physique détaillée
    val age: String = "", // Âge ou tranche d'âge
    val height: String = "", // Taille
    val hairColor: String = "", // Couleur de cheveux
    val eyeColor: String = "", // Couleur des yeux
    val bodyType: String = "", // Type de corps
    val distinctiveFeatures: List<String> = emptyList(), // Traits distinctifs
    
    val scenario: String = "", // Scénario/Background story
    val backgroundStory: String = "", // Histoire complète
    
    val temperament: String = "", // Tempérament général
    val characterTraits: List<String> = emptyList(), // Traits de caractère détaillés
    val likes: List<String> = emptyList(), // Ce qu'il/elle aime
    val dislikes: List<String> = emptyList(), // Ce qu'il/elle n'aime pas
    val skills: List<String> = emptyList(), // Compétences/capacités
    
    val gallery: List<String> = emptyList(), // URLs des images de la galerie SFW
    val galleryNSFW: List<String> = emptyList(), // URLs des images NSFW (sensuel/sexy/explicit)
    val thumbnailUrl: String = "", // URL de la vignette générée par Pollination
    
    // Message d'accueil au démarrage de la conversation
    val greetingMessage: String = "" // Première phrase du personnage en lien avec son scénario
)

enum class CharacterCategory {
    NARUTO,
    CELEBRITY_MALE,
    CELEBRITY_FEMALE
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUrl: String? = null, // URL de l'image générée
    val videoUrl: String? = null  // URL de la vidéo générée
)

data class ChatSession(
    val character: Character,
    val messages: List<ChatMessage> = emptyList(),
    val isNSFW: Boolean = false
)
