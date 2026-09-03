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

/**
 * Remplace les jetons `{{user}}`/`{{char}}` — convention standard du format Character Card V2
 * (voir charactercard/), utilisée aussi bien dans les fiches importées que dans les personnages
 * fournis par défaut (voir `exampleDialogue` dans CharacterRepository.SampleCharacters) — par du
 * texte lisible, avant tout affichage dans le chat ou envoi au moteur d'inférence. Un modèle qui
 * voit ces jetons non résolus tels quels dans son prompt a tendance à les reproduire
 * littéralement dans ses réponses, ce qui casse immédiatement l'illusion d'une conversation
 * naturelle. Appliqué à la volée (plutôt qu'au moment de l'enregistrement) pour couvrir aussi
 * les fiches déjà en base avant cet ajout, sans migration.
 *
 * @param userName nom réel de l'utilisateur (voir [com.opencompanion.app.data.UserProfile]) si
 * renseigné dans les réglages, sinon un générique neutre — pour plus de réalisme dès que la
 * personne a pris deux secondes pour se présenter, sans rien casser sinon.
 */
fun resolveCharacterPlaceholders(text: String, character: CharacterEntity, userName: String = "Utilisateur"): String =
    text.replace("{{user}}", userName, ignoreCase = true)
        .replace("{{char}}", character.name, ignoreCase = true)
