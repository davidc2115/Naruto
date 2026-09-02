package com.opencompanion.app.prompt

import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.ChatMessageEntity
import com.opencompanion.app.data.MessageRole
import com.opencompanion.app.data.resolveCharacterPlaceholders
import com.opencompanion.app.engine.ChatTurn
import com.opencompanion.app.engine.InferenceEngine

/**
 * Construit le prompt final envoyé au moteur, à partir d'une fiche
 * personnage et de l'historique de conversation.
 *
 * Deux étapes :
 *  1. [buildTurns] traduit la fiche + l'historique en une liste de tours
 *     système/utilisateur/assistant générique, en tronquant l'historique le
 *     plus ancien pour tenir dans la fenêtre de contexte du modèle chargé.
 *  2. [InferenceEngine.applyChatTemplate] (ou, à défaut, [fallbackFormat] si
 *     le modèle ne fournit pas de patron reconnu) transforme ces tours en
 *     texte brut prêt à être tokenisé.
 */
object PromptBuilder {

    /** Marge réservée à la réponse du modèle, en tokens, en plus de [reservedForResponse]. */
    private const val SAFETY_MARGIN_TOKENS = 64

    /**
     * Instruction de langue et de ton, ajoutée en tête de **tout** prompt système, y compris
     * quand [CharacterEntity.systemPromptOverride] est renseigné (une fiche importée peut très
     * bien avoir un system prompt en anglais — sans ce garde-fou, le modèle basculerait de
     * langue au milieu de la conversation dès qu'il reproduit son registre). Explicitement
     * formulée pour éviter deux travers fréquents des petits modèles quantifiés : répondre en
     * anglais (ou mélanger les deux langues) dès que le prompt contient ne serait-ce qu'un mot
     * anglais, et produire un français correct mais mécanique/traduit plutôt qu'une réplique de
     * conversation normale.
     */
    private const val LANGUAGE_AND_TONE_DIRECTIVE =
        "Tu écris exclusivement en français, sans aucune exception : même si une partie de la " +
            "description ci-dessous est dans une autre langue, ou si l'utilisateur t'écrit dans " +
            "une autre langue, tu réponds toujours en français. Ton style est celui d'une vraie " +
            "conversation orale entre deux personnes : phrases courtes et vivantes, vocabulaire " +
            "courant, quelques hésitations ou tournures naturelles si ça correspond au " +
            "personnage. Jamais de tournure robotique, de traduction mot à mot depuis l'anglais, " +
            "de liste à puces, ni de réponse démesurément longue pour un simple message de chat."

    fun buildSystemPrompt(character: CharacterEntity): String = buildString {
        append(LANGUAGE_AND_TONE_DIRECTIVE)
        append("\n\n")
        if (character.systemPromptOverride.isNotBlank()) {
            append(resolveCharacterPlaceholders(character.systemPromptOverride, character))
            return@buildString
        }
        append("Tu incarnes ${character.name}. Reste toujours dans ce rôle et réponds à la première personne.\n\n")
        if (character.description.isNotBlank()) {
            append("Description : ${resolveCharacterPlaceholders(character.description, character)}\n")
        }
        if (character.personality.isNotBlank()) {
            append("Personnalité : ${resolveCharacterPlaceholders(character.personality, character)}\n")
        }
        if (character.scenario.isNotBlank()) {
            append("Contexte : ${resolveCharacterPlaceholders(character.scenario, character)}\n")
        }
        if (character.exampleDialogue.isNotBlank()) {
            append("\nExemples de style de réponse :\n${resolveCharacterPlaceholders(character.exampleDialogue, character)}\n")
        }
    }.trim()

    /**
     * @param contextSize taille de contexte (en tokens) du modèle actuellement chargé.
     * @param reservedForResponse tokens laissés libres pour la réponse à venir.
     */
    fun buildTurns(
        character: CharacterEntity,
        history: List<ChatMessageEntity>,
        newUserMessage: String,
        engine: InferenceEngine,
        contextSize: Int,
        reservedForResponse: Int,
    ): List<ChatTurn> {
        val systemPrompt = buildSystemPrompt(character)
        val budget = (contextSize - reservedForResponse - SAFETY_MARGIN_TOKENS).coerceAtLeast(256)

        var used = engine.tokenCount(systemPrompt) + engine.tokenCount(newUserMessage)
        val kept = ArrayDeque<ChatTurn>()

        // On garde le maximum d'historique récent qui tient dans le budget, du plus récent
        // vers le plus ancien (les messages trop anciens sont simplement oubliés — pas de
        // résumé automatique dans cette première version, voir docs/ROADMAP.md).
        for (message in history.asReversed()) {
            val turn = ChatTurn(
                role = if (message.role == MessageRole.USER) "user" else "assistant",
                content = message.content,
            )
            val cost = engine.tokenCount(message.content)
            if (used + cost > budget) break
            used += cost
            kept.addFirst(turn)
        }

        return buildList {
            add(ChatTurn(role = "system", content = systemPrompt))
            addAll(kept)
            add(ChatTurn(role = "user", content = newUserMessage))
        }
    }

    fun buildPrompt(
        character: CharacterEntity,
        history: List<ChatMessageEntity>,
        newUserMessage: String,
        engine: InferenceEngine,
        contextSize: Int,
        reservedForResponse: Int,
    ): String {
        val turns = buildTurns(character, history, newUserMessage, engine, contextSize, reservedForResponse)
        return engine.applyChatTemplate(turns, addAssistant = true) ?: fallbackFormat(turns)
    }

    /** Format générique utilisé quand le modèle ne fournit aucun patron de dialogue reconnu. */
    private fun fallbackFormat(turns: List<ChatTurn>): String = buildString {
        for (turn in turns) {
            val tag = when (turn.role) {
                "system" -> "system"
                "assistant" -> "assistant"
                else -> "user"
            }
            append("<|$tag|>\n${turn.content}\n")
        }
        append("<|assistant|>\n")
    }

    // --- Backend Gemini Nano (AICore) ------------------------------------------------------

    /** Estimation grossière (≈ 4 caractères/token) utilisée uniquement pour respecter le
     *  budget de [buildNanoPrompt] : Gemini Nano n'expose pas de tokenizer côté app (contrairement
     *  à [InferenceEngine.tokenCount] pour llama.cpp), donc pas de compte exact possible ici. */
    private fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)

    /**
     * Construit un prompt en langage naturel (pas de patron de dialogue propre à un modèle,
     * Gemini Nano suit des instructions directement) pour le backend AICore, en respectant le
     * budget strict imposé par AICore (~4000 tokens en entrée+sortie au total — voir
     * docs/MODELES_ET_AICORE.md). L'historique le plus ancien est tronqué en premier, comme
     * pour [buildTurns].
     */
    fun buildNanoPrompt(
        character: CharacterEntity,
        history: List<ChatMessageEntity>,
        newUserMessage: String,
        maxOutputTokens: Int = 512,
    ): String {
        // Marge de sécurité généreuse : ~4000 tokens au total pour AICore, on réserve la sortie
        // demandée plus une marge, et on garde le reste pour system + historique + message.
        val budget = (NANO_TOKEN_BUDGET - maxOutputTokens - SAFETY_MARGIN_TOKENS).coerceAtLeast(256)
        val systemPrompt = buildSystemPrompt(character)

        var used = estimateTokens(systemPrompt) + estimateTokens(newUserMessage)
        val kept = ArrayDeque<ChatTurn>()
        for (message in history.asReversed()) {
            val turn = ChatTurn(
                role = if (message.role == MessageRole.USER) "user" else "assistant",
                content = message.content,
            )
            val cost = estimateTokens(message.content)
            if (used + cost > budget) break
            used += cost
            kept.addFirst(turn)
        }

        return buildString {
            append(systemPrompt)
            append("\n\n")
            if (kept.isNotEmpty()) {
                append("Historique récent de la conversation :\n")
                for (turn in kept) {
                    val speaker = if (turn.role == "user") "Utilisateur" else character.name
                    append("$speaker : ${turn.content}\n")
                }
                append("\n")
            }
            append("Utilisateur : $newUserMessage\n")
            append("${character.name} :")
        }
    }

    private const val NANO_TOKEN_BUDGET = 4000
}
