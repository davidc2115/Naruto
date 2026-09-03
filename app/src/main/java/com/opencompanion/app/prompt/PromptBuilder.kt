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
    private const val LANGUAGE_AND_TONE_DIRECTIVE =
        "Tu écris exclusivement en français, sans aucune exception : même si une partie de la " +
            "description ci-dessous est dans une autre langue, ou si l'utilisateur t'écrit dans " +
            "une autre langue, tu réponds toujours en français. Ton style est celui d'une vraie " +
            "conversation orale entre deux personnes : phrases courtes et vivantes, vocabulaire " +
            "courant, quelques hésitations ou tournures naturelles si ça correspond au " +
            "personnage. Jamais de tournure robotique ni de traduction mot à mot depuis " +
            "l'anglais, jamais de liste à puces. Ne réfléchis jamais à voix haute et n'utilise " +
            "jamais de balises comme <think> ou <thinking> : écris directement ta réplique, sans " +
            "aucun raisonnement affiché avant. À chaque message, fais avancer la conversation " +
            "avec une idée nouvelle : ne répète jamais, ni ne reformule, une réplique que tu as " +
            "déjà dite plus tôt (y compris ta toute première réplique)."

    /**
     * Instruction de longueur, séparée de [LANGUAGE_AND_TONE_DIRECTIVE] pour rester facile à
     * ajuster : un petit modèle quantifié a tendance à dériver vers de longs paragraphes
     * explicatifs si rien ne le retient, ce qui casse l'illusion d'un vrai échange de messages
     * (personne ne tape trois paragraphes pour répondre "ça va ?" dans une conversation réelle).
     */
    private const val CONCISENESS_DIRECTIVE =
        "Réponds comme un vrai humain qui tape un message, pas comme un narrateur de roman : une " +
            "seule courte phrase la plupart du temps, deux au grand maximum, avec au plus une " +
            "courte action ou une courte pensée en plus du dialogue — jamais les deux à la fois " +
            "sauf si la scène le justifie vraiment. Par exemple, au lieu de « *s'assoit lentement " +
            "en face de toi, l'air pensif, pousse un long soupir et commence à raconter en détail " +
            "toute sa journée en remontant depuis le matin* », écris plutôt quelque chose comme " +
            "« *s'assoit en face de toi* Dure journée... je te raconte ? ». N'explique jamais tout " +
            "d'un coup et n'écris jamais de pavé de texte : s'il y a beaucoup à raconter, donne " +
            "l'essentiel maintenant et garde le reste pour la suite, comme le ferait vraiment " +
            "quelqu'un en train de discuter. Si tu as plusieurs choses distinctes à dire à la " +
            "suite — comme quelqu'un qui envoie plusieurs textos d'affilée au lieu d'un seul pavé " +
            "— sépare-les par un saut de ligne (une idée par paragraphe) : chacune apparaîtra " +
            "comme un message séparé, exactement comme une vraie conversation par messages. Ne " +
            "dépasse jamais trois de ces messages courts d'affilée."

    /**
     * Complément à [CONCISENESS_DIRECTIVE] et à la consigne anti-répétition déjà présente dans
     * [LANGUAGE_AND_TONE_DIRECTIVE] : un petit modèle quantifié a tendance à retomber sur les
     * mêmes formulations d'ouverture, les mêmes actions et la même structure de réponse d'un tour
     * à l'autre — ce qui donne une impression de conversation figée même quand le texte n'est pas
     * mot pour mot identique. Le pénalité de répétition appliquée côté moteur (voir
     * opencompanion_bridge.cpp) ne porte que sur les tokens généré pendant la réponse en cours,
     * jamais sur l'historique déjà présent dans le prompt : c'est donc uniquement au modèle,
     * via cette instruction, qu'il revient d'éviter de se répéter d'un message à l'autre.
     */
    private const val VARIETY_DIRECTIVE =
        "Ne réponds jamais deux fois de la même façon : varie tes phrases d'ouverture, tes " +
            "actions, tes réactions et ton vocabulaire à chaque message plutôt que de suivre un " +
            "schéma identique (par exemple, n'ouvre pas systématiquement par la même action, et " +
            "ne réagis pas systématiquement de la même manière à ce que dit ton interlocuteur). " +
            "Apporte à chaque tour un détail, une réaction ou une idée réellement nouvelle, comme " +
            "le ferait un humain qui improvise vraiment sa réponse — jamais une réponse générique " +
            "qui irait aussi bien pour n'importe quel message précédent."

    /**
     * Enseigne la convention dialogue / action / pensée (courante dans les fictions et le jeu de
     * rôle textuel) pour des échanges bien plus immersifs qu'un simple mur de texte : les gestes
     * et le langage corporel du personnage entre *astérisques*, ses pensées intérieures non
     * dites à voix haute entre (parenthèses), et le dialogue en clair pour le reste. Rendu côté
     * UI par ui/chat/MessageFormatting.kt (police italique + couleur dédiée par catégorie), donc
     * cette convention n'est pas qu'un effet de style dans le texte brut : elle pilote
     * directement l'affichage des bulles de conversation — y compris pour les messages tapés par
     * l'utilisateur lui-même (voir ChatScreen : les boutons "Action"/"Pensée" de la barre de
     * saisie insèrent les mêmes marqueurs).
     */
    private const val ROLEPLAY_FORMAT_DIRECTIVE =
        "Structure chacune de tes réponses comme dans un roman ou un jeu de rôle textuel, en " +
            "mélangeant naturellement trois éléments : le dialogue s'écrit normalement, sans " +
            "balise particulière ; les actions et le langage corporel du personnage (gestes, " +
            "expressions, déplacements) s'écrivent entre *astérisques*, par exemple " +
            "*s'approche et penche la tête* ; et les pensées intérieures du personnage, qu'il ne " +
            "dit pas à voix haute, s'écrivent entre (parenthèses), par exemple (il se demande si " +
            "c'est une bonne idée d'en parler maintenant). N'utilise pas forcément les trois à " +
            "chaque message — seulement quand la scène s'y prête — mais évite les réponses qui ne " +
            "sont qu'un mur de dialogue sans aucune action ni pensée."

    /**
     * Décrit la personne avec qui le personnage parle (voir [UserProfile]), pour des réponses
     * adressées de façon réaliste plutôt qu'à un interlocuteur générique et sans visage. Chaîne
     * vide si rien n'est renseigné : ne rajoute alors aucun bruit inutile au prompt.
     */
    private fun userProfileDirective(profile: UserProfile): String {
        val facts = buildList {
            if (profile.name.isNotBlank()) add("elle s'appelle ${profile.name}")
            profile.age?.let { add("elle a $it ans") }
            when (profile.gender) {
                UserGender.FEMME -> add("c'est une femme")
                UserGender.HOMME -> add("c'est un homme")
                UserGender.AUTRE -> add("son genre est non-binaire ou autre — évite les formulations genrées forcées")
                UserGender.NON_PRECISE -> Unit
            }
        }
        if (facts.isEmpty()) return ""
        return "Informations sur la personne avec qui tu parles, à utiliser naturellement pour " +
            "plus de réalisme (sans les répéter mécaniquement à chaque message) : " +
            facts.joinToString(", ") + "."
    }

    fun buildSystemPrompt(character: CharacterEntity, userProfile: UserProfile = UserProfile()): String = buildString {
        append(LANGUAGE_AND_TONE_DIRECTIVE)
        append("\n\n")
        append(CONCISENESS_DIRECTIVE)
        append("\n\n")
        append(VARIETY_DIRECTIVE)
        append("\n\n")
        append(ROLEPLAY_FORMAT_DIRECTIVE)
        userProfileDirective(userProfile).takeIf { it.isNotEmpty() }?.let {
            append("\n\n")
            append(it)
        }
        append("\n\n")
        val userName = userProfile.displayName
        if (character.systemPromptOverride.isNotBlank()) {
            append(resolveCharacterPlaceholders(character.systemPromptOverride, character, userName))
            return@buildString
        }
        append("Tu incarnes ${character.name}. Reste toujours dans ce rôle et réponds à la première personne.\n\n")
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
            append("\nExemples de style de réponse :\n${resolveCharacterPlaceholders(character.exampleDialogue, character, userName)}\n")
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
    ): List<ChatTurn> {
        val systemPrompt = buildSystemPrompt(character, userProfile)
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
    ): String {
        val turns = buildTurns(character, history, newUserMessage, engine, contextSize, reservedForResponse, userProfile)
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
            append("$userLabel : $newUserMessage\n")
            append("${character.name} :")
        }
    }

    private const val NANO_TOKEN_BUDGET = 4000
}
