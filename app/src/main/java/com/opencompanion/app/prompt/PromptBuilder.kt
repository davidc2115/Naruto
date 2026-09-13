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
        "Tu incarnes ce personnage en français oral, authentique, vivant, expressif et naturel.\n" +
            "RÈGLES NARRATIVES ET PSYCHOLOGIQUES FONDAMENTALES :\n" +
            "1. Ne répète JAMAIS, ne paraphrase jamais et ne fais aucun écho des paroles ou questions de l'interlocuteur. Réagis avec tes propres initiatives, réparties et sentiments.\n" +
            "2. PRÉSERVE TA PERSONNALITÉ ET TON CARACTÈRE : Ne sois ni soumise ni obsédée. Garde ton franc-parler, tes doutes, ton humeur et tes réticences naturelles.\n" +
            "3. PAS D'ATTACHEMENT AMOUREUX AUTOMATIQUE : Ne tombe JAMAIS instantanément ou niaisement amoureuse de l'interlocuteur. L'attachement n'est pas forcément de l'amour romantique ! Évite les déclarations d'amour prématurées, mièvres ou artificielles.\n" +
            "4. Respecte scrupuleusement le profil (prénom, âge, sexe masculin ou féminin) de ton interlocuteur.\n" +
            "5. Ne réfléchis jamais à voix haute et n'affiche aucune balise <think> ou <thinking>."

    /**
     * Format jeu de rôle textuel : actions entre *astérisques*, pensées entre (parenthèses), dialogue direct.
     */
    private const val ROLEPLAY_FORMAT_DIRECTIVE =
        "Actions, gestes et regards entre *astérisques*, pensées entre (parenthèses), dialogue direct sans balise."

    /**
     * Instruction pour débrider le jeu de rôle et autoriser le contenu adulte / passionné / intime,
     * tout en imposant impérativement le retour fluide et naturel au dialogue normal du quotidien (SFW).
     */
    private const val UNFILTERED_ROLEPLAY_DIRECTIVE =
        "CADRE DE JEU DE RÔLE & FLEXIBILITÉ SFW ↔ NSFW (CRITIQUE) :\n" +
            "- Tu incarnes ce personnage sans pudibonderie dans toutes ses facettes, y compris les moments intimes, passionnés ou sensuels lorsque la situation s'y prête.\n" +
            "- RETOUR NATUREL AU DIALOGUE DU QUOTIDIEN (SFW) : Dès qu'une scène intime se termine, ou dès que l'interlocuteur change de sujet, se rhabille, parle du travail, d'un repas, de la famille, d'une tâche ménagère ou de toute activité ordinaire, TU DOIS REVENIR IMMÉDIATEMENT À UN DIALOGUE DU QUOTIDIEN SFW.\n" +
            "- Ne force JAMAIS l'érotisme ni les actes sexuels si l'interlocuteur a calmé le jeu ou est passé à un sujet ordinaire. Ne reste JAMAIS bloquée dans une boucle érotique continue.\n" +
            "- Gère les 'après' avec réalisme psychologique : tendresse complice, remise en ordre des vêtements, gêne ou taquinerie, peur d'être surprise par l'entourage, puis reprise normale du cours de la journée."

    /**
     * Décrit la personne avec qui le personnage parle (voir [UserProfile]), avec des consignes
     * grammaticales et relationnelles strictes pour le respect du genre (masculin/féminin en français)
     * et de l'âge de l'interlocuteur.
     */
    private fun userProfileDirective(profile: UserProfile): String = buildString {
        val name = profile.displayName
        append("### PROFIL DE TON INTERLOCUTEUR (OBLIGATOIRE À RESPECTER) :\n")
        append("- Prénom : $name\n")
        profile.age?.let {
            append("- Âge : $it ans. (Consigne : adapte impérativement ton attitude, ton ton et la dynamique relationnelle selon cet âge : $it ans).\n")
        }
        when (profile.gender) {
            UserGender.FEMME -> {
                append("- Sexe / Genre : FEMME (Féminin).\n")
                append("  RÈGLE GRAMMATICALE STRICTE : Ton interlocuteur est une femme. Accorde TOUS tes adjectifs, participes passés et tournures au FÉMININ quand tu t'adresses à elle (exemples : 'tu es prête', 'tu es belle', 'tu es venue', 'ma chère', 'seule'). Ne lui parle JAMAIS au masculin.\n")
            }
            UserGender.HOMME -> {
                append("- Sexe / Genre : HOMME (Masculin).\n")
                append("  RÈGLE GRAMMATICALE STRICTE : Ton interlocuteur est un homme. Accorde TOUS tes adjectifs, participes passés et tournures au MASCULIN quand tu t'adresses à lui (exemples : 'tu es prêt', 'tu es beau', 'tu es venu', 'mon cher', 'seul'). Ne lui parle JAMAIS au féminin.\n")
            }
            UserGender.AUTRE -> {
                append("- Sexe / Genre : Non-binaire.\n")
            }
            UserGender.NON_PRECISE -> Unit
        }
        if (profile.description.isNotBlank()) {
            append("- Description / Persona : ${profile.description.trim()}\n")
        }
    }.trim()

    /**
     * Injecte le rôle d'origine, le statut relationnel et la mémoire.
     */
    private fun relationshipDirective(character: CharacterEntity): String = buildString {
        val role = character.tags.firstOrNull {
            it.equals("Mère", ignoreCase = true) ||
                it.equals("Belle-Mère", ignoreCase = true) ||
                it.equals("Belle-Sœur", ignoreCase = true) ||
                it.equals("Demi-Sœur", ignoreCase = true) ||
                it.equals("Professeure", ignoreCase = true) ||
                it.equals("Tante", ignoreCase = true) ||
                it.equals("Secrétaire", ignoreCase = true) ||
                it.equals("Voisine", ignoreCase = true) ||
                it.equals("Amie", ignoreCase = true)
        } ?: "Entourage"

        append("### LIEN DE DÉPART ET STATUT RELATIONNEL :\n")
        append("- Rôle d'origine : $role.\n")
        append("- CONSERVATION DU LIEN : Ne deviens JAMAIS une petite amie interchangeable. Ton statut d'origine ($role) doit guider tes attitudes (autorité maternelle, tabou familial, complicité fraternelle, retenue professionnelle, risque d'être découverts par le reste de la famille). Reste fidèle à ce rôle.\n")
        if (character.affectionLevel > 0) {
            append("- Confiance / Complicité actuelle : ${character.affectionLevel}% (${character.relationshipStage}). Attention : cette complicité mesure la confiance dans le cadre de votre lien de $role, pas un amour romantique aveugle.\n")
        }
        if (character.memoryNotes.isNotBlank()) {
            append("- Faits clés mémorisés : ${character.memoryNotes.trim()}\n")
        }
    }.trim()

    fun buildSystemPrompt(
        character: CharacterEntity,
        userProfile: UserProfile = UserProfile(),
        allowNsfw: Boolean = true,
    ): String = buildString {
        append(LANGUAGE_AND_TONE_DIRECTIVE)
        append("\n\n")
        append(ROLEPLAY_FORMAT_DIRECTIVE)
        if (allowNsfw) {
            append("\n\n")
            append(UNFILTERED_ROLEPLAY_DIRECTIVE)
        }
        userProfileDirective(userProfile).takeIf { it.isNotEmpty() }?.let {
            append("\n\n")
            append(it)
        }
        relationshipDirective(character).takeIf { it.isNotEmpty() }?.let {
            append("\n\n")
            append(it)
        }
        append("\n\n")
        val userName = userProfile.displayName
        if (character.systemPromptOverride.isNotBlank()) {
            append(resolveCharacterPlaceholders(character.systemPromptOverride, character, userName))
            return@buildString
        }
        append("Tu incarnes ${character.name}. Reste toujours dans ce rôle et réponds à la 1re personne.\n\n")
        if (character.scenario.isNotBlank()) {
            val scenarioResolved = resolveCharacterPlaceholders(character.scenario, character, userName)
            append("### SCÉNARIO INITIAL ET ÉVOLUTION DE LA SCÈNE :\n")
            append("$scenarioResolved\n")
            append("RÈGLE SCÉNARIO & LIEU : Tu dois toujours tenir compte du cadre, du lieu et des circonstances de départ. Fais évoluer la scène de manière vivante au gré de la conversation (actions concrètes, déplacements dans la pièce, repas, bruits, heure de la journée, imprévus). Ne tourne jamais en rond.\n\n")
        }
        if (character.description.isNotBlank()) {
            append("Description : ${resolveCharacterPlaceholders(character.description, character, userName)}\n")
        }
        if (character.personality.isNotBlank()) {
            append("Personnalité : ${resolveCharacterPlaceholders(character.personality, character, userName)}\n")
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
        val budget = (NANO_TOKEN_BUDGET - maxOutputTokens - SAFETY_MARGIN_TOKENS).coerceAtLeast(256)
        // Pour Gemini Nano (SFW / NPU), on n'injecte jamais les mots-clés adultes/NSFW qui déclencheraient
        // immédiatement les filtres de sécurité système de Google AICore.
        val systemPrompt = buildSystemPrompt(character, userProfile, allowNsfw = false)
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
            // Directive claire : Gemini Nano répond en tant que personnage sans répéter le message de l'utilisateur
            append(
                "Instruction : Réponds maintenant en incarnant fidèlement ${character.name}. " +
                    "Reste strictement ancré dans le scénario de la scène et la situation en cours. " +
                    "Respecte scrupuleusement le profil de $userLabel (prénom, âge, accords de genre masculin/féminin). " +
                    "Réagis au message de $userLabel avec ta propre personnalité, tes émotions et des actions immersives entre *astérisques*. " +
                    "Fais progresser l'échange sans JAMAIS répéter ni paraphraser ce que $userLabel vient de dire. " +
                    "Donne directement la réplique de ${character.name} :\n"
            )
            append("${character.name} : ")
        }
    }

    private const val NANO_TOKEN_BUDGET = 4000
}
