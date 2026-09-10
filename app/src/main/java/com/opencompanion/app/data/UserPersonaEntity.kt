package com.opencompanion.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persona utilisateur : une identité que l'utilisateur peut endosser dans ses conversations
 * (ex. "Alex, 28 ans, écrivain timide" pour une histoire, "Capitaine Vega" pour une autre) — la
 * fonctionnalité "personas utilisateur multiples" des apps façon RosyTalk. Remplace le profil
 * unique auparavant stocké dans les réglages ([SettingsRepository.UserProfile]) par plusieurs
 * profils nommés, sélectionnables indépendamment par personnage (voir
 * [CharacterEntity.activePersonaId]).
 *
 * [isDefault] marque le persona utilisé quand un personnage n'a pas de choix explicite ; un seul
 * persona devrait être marqué par défaut à la fois (voir CharacterRepository.setDefaultPersona,
 * qui garantit cette unicité).
 */
@Entity(tableName = "user_personas")
data class UserPersonaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val age: Int? = null,
    /** Nom brut de [UserGender] (FEMME/HOMME/AUTRE/NON_PRECISE) — stocké en texte plutôt qu'en
     *  type Room dédié pour rester cohérent avec le reste du schéma (voir Converters.kt). */
    val gender: String = "NON_PRECISE",
    /** Courte description/bio injectée dans le prompt système, ex. "écrivain timide qui n'ose
     *  jamais dire ce qu'il pense vraiment" — le cœur de l'identité du persona au-delà du nom. */
    val description: String = "",
    val avatarPath: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
