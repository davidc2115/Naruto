package com.opencompanion.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Personnage stocké localement. Les champs reprennent volontairement le
 * vocabulaire de la spec ouverte "Character Card V2" (description,
 * personality, scenario, first_mes, mes_example…) pour que l'import/export
 * (voir charactercard/) reste une simple correspondance 1:1 — pas de format
 * maison à traduire.
 *
 * Tous les personnages fournis avec l'app sont fictifs et originaux, tout
 * public. Rien n'empêche d'en créer ou d'en importer d'autres : c'est un
 * choix de contenu par défaut, pas une limite technique.
 */
@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val exampleDialogue: String = "",
    val systemPromptOverride: String = "",
    val avatarPath: String? = null,
    val tagsCsv: String = "",
    val creatorNotes: String = "",
    val creator: String = "",
    val characterVersion: String = "",
    val isBundledSample: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val tags: List<String>
        get() = tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
