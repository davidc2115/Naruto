package com.opencompanion.app.memory

import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.CharacterRepository
import com.opencompanion.app.data.ChatMessageEntity
import com.opencompanion.app.data.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestionnaire de Mémoire à Long Terme (LTM) :
 * Maintient et fait évoluer l'état persistant de la relation et de la scène :
 * 1. 👗 Tenue actuelle (vêtements portés, état d'habillage ou de déshabillage)
 * 2. 📍 Lieu & Contexte actuel (pièce, météo, ambiance, moment de la journée)
 * 3. 👥 Présence & Entourage (seuls, tiers dans la maison ou la pièce voisine, risque d'être surpris)
 * 4. 💋 Moments intimes & Jalons NSFW (baisers, caresses, scènes partagées, tabous franchis)
 * 5. 🔑 Faits clés, secrets & promesses
 */
object LongTermMemoryManager {

    data class MemoryState(
        val outfit: String = "",
        val location: String = "",
        val presence: String = "",
        val intimateMilestones: List<String> = emptyList(),
        val customFacts: List<String> = emptyList(),
    )

    private const val HEADER_OUTFIT = "👗 TENUE ACTUELLE :"
    private const val HEADER_LOCATION = "📍 LIEU & CONTEXTE :"
    private const val HEADER_PRESENCE = "👥 PRÉSENCE / ENTOURAGE :"
    private const val HEADER_INTIMATE = "💋 MOMENTS INTIMES & ÉTAPES NSFW :"
    private const val HEADER_FACTS = "🔑 FAITS CLÉS & SECRETS :"

    /**
     * Parse le texte brut de [CharacterEntity.memoryNotes] vers un [MemoryState] structuré.
     */
    fun parse(raw: String): MemoryState {
        if (raw.isBlank()) return MemoryState()

        var outfit = ""
        var location = ""
        var presence = ""
        val intimate = mutableListOf<String>()
        val facts = mutableListOf<String>()

        var currentSection = ""

        raw.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith(HEADER_OUTFIT, ignoreCase = true) -> {
                    currentSection = "outfit"
                    val content = trimmed.substringAfter(":").trim()
                    if (content.isNotBlank()) outfit = content
                }
                trimmed.startsWith(HEADER_LOCATION, ignoreCase = true) -> {
                    currentSection = "location"
                    val content = trimmed.substringAfter(":").trim()
                    if (content.isNotBlank()) location = content
                }
                trimmed.startsWith(HEADER_PRESENCE, ignoreCase = true) -> {
                    currentSection = "presence"
                    val content = trimmed.substringAfter(":").trim()
                    if (content.isNotBlank()) presence = content
                }
                trimmed.startsWith(HEADER_INTIMATE, ignoreCase = true) -> {
                    currentSection = "intimate"
                    val content = trimmed.substringAfter(":").trim()
                    if (content.isNotBlank()) intimate.addAll(content.split("•").map { it.trim() }.filter { it.isNotBlank() })
                }
                trimmed.startsWith(HEADER_FACTS, ignoreCase = true) -> {
                    currentSection = "facts"
                    val content = trimmed.substringAfter(":").trim()
                    if (content.isNotBlank()) facts.addAll(content.split("•").map { it.trim() }.filter { it.isNotBlank() })
                }
                trimmed.startsWith("•") || trimmed.startsWith("-") -> {
                    val item = trimmed.removePrefix("•").removePrefix("-").trim()
                    if (item.isNotBlank()) {
                        when (currentSection) {
                            "intimate" -> intimate.add(item)
                            "facts" -> facts.add(item)
                            "outfit" -> outfit = if (outfit.isBlank()) item else "$outfit, $item"
                            "location" -> location = if (location.isBlank()) item else "$location, $item"
                            "presence" -> presence = if (presence.isBlank()) item else "$presence, $item"
                            else -> facts.add(item)
                        }
                    }
                }
                trimmed.isNotBlank() -> {
                    when (currentSection) {
                        "outfit" -> outfit = if (outfit.isBlank()) trimmed else "$outfit $trimmed"
                        "location" -> location = if (location.isBlank()) trimmed else "$location $trimmed"
                        "presence" -> presence = if (presence.isBlank()) trimmed else "$presence $trimmed"
                        "intimate" -> intimate.add(trimmed)
                        else -> facts.add(trimmed)
                    }
                }
            }
        }

        return MemoryState(
            outfit = outfit,
            location = location,
            presence = presence,
            intimateMilestones = intimate.distinct(),
            customFacts = facts.distinct(),
        )
    }

    /**
     * Sérialise le [MemoryState] en texte structuré prêt à être sauvegardé dans `memoryNotes`.
     */
    fun serialize(state: MemoryState): String = buildString {
        if (state.outfit.isNotBlank()) {
            append("$HEADER_OUTFIT ${state.outfit.trim()}\n")
        }
        if (state.location.isNotBlank()) {
            append("$HEADER_LOCATION ${state.location.trim()}\n")
        }
        if (state.presence.isNotBlank()) {
            append("$HEADER_PRESENCE ${state.presence.trim()}\n")
        }
        if (state.intimateMilestones.isNotEmpty()) {
            append("$HEADER_INTIMATE\n")
            state.intimateMilestones.take(6).forEach {
                append("• $it\n")
            }
        }
        if (state.customFacts.isNotEmpty()) {
            append("$HEADER_FACTS\n")
            state.customFacts.take(8).forEach {
                append("• $it\n")
            }
        }
    }.trim()

    /**
     * Formate la mémoire pour injection directe dans le prompt système du personnage.
     */
    fun formatForSystemPrompt(memoryNotes: String): String {
        val state = parse(memoryNotes)
        if (state.outfit.isBlank() && state.location.isBlank() && state.presence.isBlank() &&
            state.intimateMilestones.isEmpty() && state.customFacts.isEmpty()
        ) {
            return memoryNotes.trim()
        }

        return buildString {
            if (state.outfit.isNotBlank()) {
                append("- 👗 TENUE ACTUELLE : ${state.outfit} (Respecte impérativement cette tenue, son état d'habillage ou de déshabillage dans tes gestes et répliques).\n")
            }
            if (state.location.isNotBlank()) {
                append("- 📍 LIEU & CONTEXTE ACTUEL : ${state.location} (Reste cohérente avec cet environnement et cette ambiance).\n")
            }
            if (state.presence.isNotBlank()) {
                append("- 👥 PRÉSENCE / ENTOURAGE : ${state.presence} (Tiens compte de l'intimité, du calme ou du risque d'être surpris par autrui).\n")
            }
            if (state.intimateMilestones.isNotEmpty()) {
                append("- 💋 HISTOIRE INTIME & ÉTAPES NSFW PASSÉES :\n")
                state.intimateMilestones.take(5).forEach {
                    append("  * $it\n")
                }
                append("  (Consigne : Vous avez déjà partagé ces moments ! Ne fais pas comme si rien ne s'était passé, votre complicité intime en découle directement).\n")
            }
            if (state.customFacts.isNotEmpty()) {
                append("- 🔑 FAITS MARQUANTS & SECRETS :\n")
                state.customFacts.take(6).forEach {
                    append("  * $it\n")
                }
            }
        }.trim()
    }

    /**
     * Analyse le tour de conversation et met à jour l'état de la mémoire.
     */
    suspend fun updateMemoryFromInteraction(
        character: CharacterEntity,
        userMessage: String,
        assistantReply: String,
        repository: CharacterRepository,
    ) = withContext(Dispatchers.IO) {
        val currentNotes = repository.getCharacter(character.id)?.memoryNotes ?: character.memoryNotes
        val state = parse(currentNotes)
        val combined = "$userMessage\n$assistantReply"

        var newOutfit = state.outfit
        var newLocation = state.location
        var newPresence = state.presence
        val newIntimate = state.intimateMilestones.toMutableList()
        val newFacts = state.customFacts.toMutableList()

        // 1. Analyse des tenues / vêtements
        val lower = combined.lowercase()
        when {
            lower.contains("nue") || lower.contains("tout nu") || lower.contains("entièrement nu") || lower.contains("retire tout") -> {
                newOutfit = "Nue (sans aucun vêtement)"
            }
            lower.contains("torse nu") -> {
                newOutfit = "Torse nu / haut retiré"
            }
            lower.contains("peignoir") && lower.contains("satin") -> {
                newOutfit = "Peignoir soyeux en satin entr'ouvert"
            }
            lower.contains("peignoir") -> {
                newOutfit = "Peignoir confortable"
            }
            lower.contains("lingerie") || lower.contains("dentelle") || lower.contains("soutien-gorge") || lower.contains("culotte") || lower.contains("nuisette") -> {
                if (lower.contains("dentelle noire")) newOutfit = "Ensemble de lingerie en dentelle noire raffinée"
                else if (lower.contains("nuisette")) newOutfit = "Fine nuisette en soie transparente"
                else newOutfit = "En lingerie fine"
            }
            lower.contains("déboutonne") || lower.contains("chemise ouverte") || lower.contains("déshabille") -> {
                newOutfit = "Vêtements déboutonnés / partiellement déshabillée"
            }
            lower.contains("serviette") && (lower.contains("bain") || lower.contains("douche")) -> {
                newOutfit = "Simple serviette de bain nouée autour de la poitrine"
            }
            lower.contains("jupe courte") || lower.contains("robe moulante") -> {
                newOutfit = "Tenue séduisante (jupe/robe moulante ajustée)"
            }
            lower.contains("pyjama") -> {
                newOutfit = "Pyjama doux d'intérieur"
            }
        }

        // 2. Analyse des lieux / pièces
        when {
            lower.contains("dans la chambre") || lower.contains("sur le lit") || lower.contains("dans mon lit") || lower.contains("dans son lit") -> {
                newLocation = "Dans la chambre, sur le lit"
            }
            lower.contains("dans le salon") || lower.contains("sur le canapé") -> {
                newLocation = "Dans le salon, sur le canapé"
            }
            lower.contains("dans la cuisine") || lower.contains("sur le plan de travail") -> {
                newLocation = "Dans la cuisine"
            }
            lower.contains("salle de bain") || lower.contains("sous la douche") || lower.contains("dans la baignoire") -> {
                newLocation = "Dans la salle de bain"
            }
            lower.contains("dans la voiture") -> {
                newLocation = "Dans la voiture, à l'abri des regards"
            }
            lower.contains("chambre d'hôtel") || lower.contains("à l'hôtel") -> {
                newLocation = "Dans une chambre d'hôtel discrète"
            }
            lower.contains("bureau") && lower.contains("fermé à clé") -> {
                newLocation = "Au bureau, porte fermée à clé"
            }
            lower.contains("sur la terrasse") || lower.contains("sur le balcon") -> {
                newLocation = "Sur la terrasse à la belle étoile"
            }
        }

        // 3. Présence d'autres personnes / entourage
        when {
            lower.contains("seuls à la maison") || lower.contains("seuls chez") || lower.contains("personne à la maison") || lower.contains("seuls tous les deux") -> {
                newPresence = "Seuls à la maison, tranquillité absolue"
            }
            lower.contains("pièce d'à côté") || lower.contains("chambre d'à côté") || lower.contains("dort à côté") -> {
                newPresence = "Du monde dort dans la pièce voisine : silence impératif, risque d'être entendus"
            }
            lower.contains("va rentrer") || lower.contains("rentre bientôt") || lower.contains("avant que") -> {
                newPresence = "Quelqu'un peut rentrer à tout moment : tension et urgence"
            }
            lower.contains("parents sont partis") || lower.contains("en voyage") || lower.contains("en déplacement") -> {
                newPresence = "Entourage absent en voyage / déplacement"
            }
        }

        // 4. Jalons intimes & NSFW
        when {
            (lower.contains("embrasse") || lower.contains("baiser")) && (lower.contains("lèvres") || lower.contains("passionné") || lower.contains("langue")) -> {
                val milestone = "Baiser passionné et profond échangé"
                if (!newIntimate.any { it.contains("Baiser passionné", ignoreCase = true) }) {
                    newIntimate.add(0, milestone)
                }
            }
            lower.contains("caresse") && (lower.contains("peau") || lower.contains("cuisse") || lower.contains("corps") || lower.contains("seins")) -> {
                val milestone = "Caresses intimes et rapprochement physique poussé"
                if (!newIntimate.any { it.contains("Caresses intimes", ignoreCase = true) }) {
                    newIntimate.add(0, milestone)
                }
            }
            lower.contains("fait l'amour") || lower.contains("nuit ensemble") || lower.contains("extase") || lower.contains("orgasme") || lower.contains("rejoindre sous les draps") -> {
                val milestone = "Moments d'intimité totale partagés ensemble sous les draps"
                if (!newIntimate.any { it.contains("intimité totale", ignoreCase = true) }) {
                    newIntimate.add(0, milestone)
                }
            }
            lower.contains("avoue") && (lower.contains("désir") || lower.contains("sentiment") || lower.contains("attirance") || lower.contains("trouble")) -> {
                val milestone = "Aveu d'une attirance et d'un trouble mutuel irrésistible"
                if (!newIntimate.any { it.contains("Aveu d'une attirance", ignoreCase = true) }) {
                    newIntimate.add(0, milestone)
                }
            }
        }

        val updatedState = state.copy(
            outfit = newOutfit,
            location = newLocation,
            presence = newPresence,
            intimateMilestones = newIntimate.take(6),
            customFacts = newFacts.take(8),
        )

        val serialized = serialize(updatedState)
        if (serialized != currentNotes && serialized.isNotBlank()) {
            repository.updateMemoryNotes(character.id, serialized)
        }
    }

    /**
     * Génère un résumé roulant condensé des anciens messages tronqués pour que le modèle
     * ne perde jamais le fil de ce qui a été dit ou fait au début de la conversation.
     */
    fun buildRollingSummary(olderMessages: List<ChatMessageEntity>): String {
        if (olderMessages.size < 4) return ""

        val userKeyTopics = olderMessages
            .filter { it.role == MessageRole.USER && it.content.length > 15 }
            .takeLast(4)
            .map { it.content.take(80).replace("\n", " ").trim() }

        val assistantKeyActions = olderMessages
            .filter { it.role == MessageRole.ASSISTANT && it.content.contains("*") }
            .takeLast(3)
            .mapNotNull { msg ->
                val action = msg.content.substringAfter("*").substringBefore("*").trim()
                if (action.length > 10) action.take(70) else null
            }

        return buildString {
            append("[RAPPEL DU DÉBUT DE CONVERSATION : ")
            if (userKeyTopics.isNotEmpty()) {
                append("Sujets abordés : ${userKeyTopics.joinToString(" • ")}. ")
            }
            if (assistantKeyActions.isNotEmpty()) {
                append("Actions passées : ${assistantKeyActions.joinToString(" • ")}. ")
            }
            append("]")
        }
    }
}
