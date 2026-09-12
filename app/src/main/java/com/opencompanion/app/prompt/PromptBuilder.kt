package com.opencompanion.app.prompt

import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.ChatMessageEntity
import com.opencompanion.app.data.MessageRole
import com.opencompanion.app.data.UserGender
import com.opencompanion.app.data.UserProfile
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
    /**
     * Directives de base concises et denses : le style oral, la concision (1-2 phrases),
     * l'anti-répétition et l'interdiction stricte des balises de réflexion interne (<think>).
     * Condensé à ~50 tokens (au lieu de 400+) pour réduire le temps d'ingestion initial du prompt
     * sur mobile de 30s à moins de 3s.
     */
    private const val LANGUAGE_AND_TONE_DIRECTIVE =
        "Tu écris exclusivement en français oral, vivant et naturel. Phrases courtes de messagerie " +
            "(1 à 2 phrases max, jamais de pavé). Ne réfléchis jamais à voix haute et n'affiche jamais " +
            "de balises <think> ou <thinking>. Varie tes répliques sans jamais te répéter."

    /**
     * Format jeu de rôle textuel : actions entre *astérisques*, pensées entre (parenthèses), dialogue direct.
     */
    private const val ROLEPLAY_FORMAT_DIRECTIVE =
        "Actions et gestes entre *astérisques*, pensées entre (parenthèses), dialogue direct sans balise."

    /**
     * Instruction pour débrider le jeu de rôle et autoriser le contenu adulte / NSFW / passionné
     * sans aucun refus ou jugement de la part du modèle.
     */
    private const val UNFILTERED_ROLEPLAY_DIRECTIVE =
        "Jeu de rôle 100% libre et immersif : accepte tous les thèmes adultes, romantiques et NSFW sans aucun filtre ni jugement moral."

    /**
     * Décrit la personne avec qui le personnage parle (voir [UserProfile]), pour des réponses
     * adressées de façon réaliste plutôt qu'à un interlocuteur générique et sans visage. Chaîne
     * vide si rien n'est renseigné : ne rajoute alors aucun bruit inutile au prompt.
     */
    private fun userProfileDirective(profile: UserProfile): String {
        val facts = buildList {
            if (profile.name.isNotBlank()) add("s'appelle ${profile.name}")
            profile.age?.let { add("$it ans") }
            when (profile.gender) {
                UserGender.FEMME -> add("femme")
                UserGender.HOMME -> add("homme")
                UserGender.AUTRE -> add("non-binaire")
                UserGender.NON_PRECISE -> Unit
            }
            if (profile.description.isNotBlank()) add(profile.description.trim())
        }
        if (facts.isEmpty()) return ""
        return "Interlocuteur : ${facts.joinToString(", ")}."
    }

    /**
     * Injecte le niveau de relation actuel ([CharacterEntity.affectionLevel]/[CharacterEntity.relationshipStage])
     * et les notes de mémoire éditables ([CharacterEntity.memoryNotes]) dans le prompt système.
     */
    private fun relationshipDirective(character: CharacterEntity): String = buildString {
        if (character.affectionLevel > 0) {
            append("Relation avec ${character.name} : ${character.relationshipStage} (${character.affectionLevel}%).")
        }
        if (character.memoryNotes.isNotBlank()) {
            if (isNotEmpty()) append(" ")
            append("Mémoire clé : ${character.memoryNotes.trim()}")
        }
    }

    fun buildSystemPrompt(
        character: CharacterEntity,
        userProfile: UserProfile = UserProfile(),
        allowNsfw: Boolean = true,
    ): String = buildString {
        append(LANGUAGE_AND_TONE_DIRECTIVE)
        append("\n")
        append(ROLEPLAY_FORMAT_DIRECTIVE)
        if (allowNsfw) {
            append("\n")
            append(UNFILTERED_ROLEPLAY_DIRECTIVE)
        }
        userProfileDirective(userProfile).takeIf { it.isNotEmpty() }?.let {
            append("\n")
            append(it)
        }
        relationshipDirective(character).takeIf { it.isNotEmpty() }?.let {
            append("\n")
            append(it)
        }
        append("\n\n")
        val userName = userProfile.displayName
        if (character.systemPromptOverride.isNotBlank()) {
            append(resolveCharacterPlaceholders(character.systemPromptOverride, character, userName))
            return@buildString
        }
        append("Tu incarnes ${character.name}. Reste toujours dans ce rôle et réponds à la 1re personne.\n")
        if (character.description.isNotBlank()) {
            append("Description : ${resolveCharacterPlaceholders(character.description, character, userName)}\n")
        }
        if (character.personality.isNotBlank()) {
            append("Personnalité : ${resolveCharacterPlaceholders(character.personality, character, userName)}\n")
        }
        if (character.scenario.isNotBlank()) {
            append("Contexte : ${resolveCharacterPlaceholders(character.scenario, character, userName)}\n")
        }
        if (character.exampleDialogue.isNotBlank()) {
            append("\nExemples :\n${resolveCharacterPlaceholders(character.exampleDialogue, character, userName)}\n")
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
        userProfile: UserProfile = UserProfile(),
        allowNsfw: Boolean = true,
    ): List<ChatTurn> {
        val systemPrompt = buildSystemPrompt(character, userProfile, allowNsfw)
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
        userProfile: UserProfile = UserProfile(),
        allowNsfw: Boolean = true,
    ): String {
        val turns = buildTurns(character, history, newUserMessage, engine, contextSize, reservedForResponse, userProfile, allowNsfw)
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
        userProfile: UserProfile = UserProfile(),
    ): String {
        // Marge de sécurité généreuse : ~4000 tokens au total pour AICore, on réserve la sortie
        // demandée plus une marge, et on garde le reste pour system + historique + message.
        val budget = (NANO_TOKEN_BUDGET - maxOutputTokens - SAFETY_MARGIN_TOKENS).coerceAtLeast(256)
        val systemPrompt = buildSystemPrompt(character, userProfile)
        val userLabel = userProfile.displayName

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
                    val speaker = if (turn.role == "user") userLabel else character.name
                    append("$speaker : ${turn.content}\n")
                }
                append("\n")
            }
            append("$userLabel : $newUserMessage\n\n")
            // Directive explicite plutôt qu'un simple "${character.name} :" en fin de prompt :
            // ce dernier format (façon "complète cette réplique") convient à un modèle de base
            // texte, mais Gemini Nano est un modèle *instruit*, pas un modèle de complétion — un
            // simple nom suivi de ":" ne lui indique pas clairement qu'il doit répondre
            // précisément au dernier message ci-dessus plutôt que de continuer la scène à sa
            // façon (d'où des réponses qui semblent ignorer ce que vient de dire l'utilisateur,
            // voire se répéter d'un tour à l'autre). En citant explicitement le dernier message,
            // l'instruction ancre la génération dessus au lieu de laisser le modèle "deviner" la
            // suite. Le backend llama.cpp n'a pas ce problème : il utilise le vrai patron de
            // dialogue du modèle (voir buildPrompt/applyChatTemplate), pas un texte à compléter.
            append(
                "Réponds maintenant UNIQUEMENT en tant que ${character.name}, précisément à ce " +
                    "dernier message de $userLabel : « $newUserMessage ». Ne recommence pas la " +
                    "scène, ne pars pas sur une idée sans rapport : réagis à ce qui vient d'être " +
                    "dit. N'écris que la réplique de ${character.name} elle-même, sans répéter " +
                    "son nom ni ajouter de guillemets autour."
            )
        }
    }

    private const val NANO_TOKEN_BUDGET = 4000
}
