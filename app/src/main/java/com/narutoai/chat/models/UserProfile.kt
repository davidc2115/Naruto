package com.narutoai.chat.models

/**
 * Profil utilisateur pour personnaliser les conversations
 */
data class UserProfile(
    val pseudo: String = "",
    val age: Int? = null,
    val gender: Gender = Gender.NOT_SPECIFIED,
    val bio: String = ""
)

enum class Gender(val displayName: String, val pronoun: String) {
    MALE("Homme", "il"),
    FEMALE("Femme", "elle"),
    NON_BINARY("Non-binaire", "iel"),
    NOT_SPECIFIED("Non spécifié", "")
}
