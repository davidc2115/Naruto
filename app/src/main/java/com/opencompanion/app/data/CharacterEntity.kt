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
    /** Persona utilisateur (voir [UserPersonaEntity]) à utiliser dans les conversations avec ce
     *  personnage. null = utiliser le persona marqué par défaut (voir
     *  CharacterRepository.resolveActivePersona) — permet à chaque personnage/histoire d'avoir
     *  sa propre identité pour l'utilisateur sans devoir la choisir à chaque fois. */
    val activePersonaId: Long? = null,
    /** Notes libres, éditables par l'utilisateur DEPUIS le chat (pas la fiche personnage) —
     *  mémoire persistante de faits importants ("mémoire quasi illimitée pour des conversations
     *  évolutives") qui survit même quand l'historique brut est tronqué faute de place dans le
     *  contexte du modèle (voir PromptBuilder.buildTurns). */
    val memoryNotes: String = "",
    /** Niveau de relation avec ce personnage, 0-100. Progresse automatiquement d'un petit
     *  incrément à chaque message envoyé (voir CharacterRepository.incrementAffection) — un
     *  indicateur simple et honnête plutôt qu'une prétendue analyse de sentiment — et reste
     *  ajustable manuellement par l'utilisateur (voir ChatScreen, dialogue "Relation & Mémoire"). */
    val affectionLevel: Int = 0,
    /** Liste JSON des médias (photos, GIFs, vidéos) associés à la galerie de ce personnage. */
    val galleryMediaJson: String = "[]",
    /** Liste JSON des médias supprimés explicitement par l'utilisateur (pour ne jamais les réinjecter). */
    val deletedMediaJson: String = "[]",
    /** Indique si la fiche a été modifiée/personnalisée par l'utilisateur (sanctuarise les textes et images). */
    val isCustomizedByUser: Boolean = false,
) {
    val tags: List<String>
        get() = tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val galleryMedia: List<String>
        get() = runCatching {
            val trimmed = galleryMediaJson.trim()
            if (trimmed.startsWith("[")) {
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(trimmed)
            } else if (trimmed.isNotBlank()) {
                trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

    val deletedMedia: Set<String>
        get() = runCatching {
            val trimmed = deletedMediaJson.trim()
            if (trimmed.startsWith("[")) {
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(trimmed).toSet()
            } else if (trimmed.isNotBlank()) {
                trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                emptySet()
            }
        }.getOrDefault(emptySet())

    /** Étiquette qualitative dérivée de [affectionLevel], pour l'affichage et pour le prompt
     *  système — des seuils simples plutôt qu'une échelle continue, plus lisibles d'un coup
     *  d'œil pour l'utilisateur comme pour orienter le ton du modèle. */
    val relationshipStage: String
        get() = when {
            affectionLevel >= 80 -> "Confiance profonde"
            affectionLevel >= 60 -> "Forte complicité"
            affectionLevel >= 40 -> "Complicité naissante"
            affectionLevel >= 20 -> "Bonne entente"
            else -> "Contact initial"
        }
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
