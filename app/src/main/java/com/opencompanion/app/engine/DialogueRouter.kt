package com.opencompanion.app.engine

import com.opencompanion.app.data.ChatMessageEntity

/**
 * Mode de routage du dialogue pour la bascule fluide SFW ↔ NSFW.
 */
enum class DialogueMode {
    /** Analyse automatique du dialogue : SFW sur Gemini Nano (NPU ⚡), NSFW sur IA Locale non censurée (🔓). */
    AUTO_HYBRID,
    /** Force l'utilisation de Gemini Nano (NPU ultra-rapide). */
    FORCE_SFW,
    /** Force l'utilisation de l'IA locale non censurée (100% débridée, thèmes adultes/NSFW). */
    FORCE_NSFW
}

/**
 * Routeur intelligent du dialogue :
 *  1. Analyse en temps réel du texte utilisateur et du contexte pour détecter les intentions intimes / NSFW / adultes.
 *  2. Détecte les refus ou blocages de sécurité des modèles filtrés (Gemini Nano AICore).
 *  3. Permet la bascule fluide et bidirectionnelle SFW ↔ NSFW sans interruption pour l'utilisateur.
 */
object DialogueRouter {

    /**
     * Lexique de détection étendu couvrant les interactions intimes, érotiques, romantiques explicites,
     * adultes et le roleplay sans censure (en français et anglais).
     */
    private val NSFW_PATTERNS = listOf(
        // Actions physiques intimes et romantiques poussées
        "embrasse", "embrasser", "baiser", "caresse", "caresser", "corps", "lit", "déshabille",
        "déshabiller", "déshabillant", "nu", "nue", "nus", "nues", "désir", "toucher", "touche",
        "peau", "sexe", "sexuel", "sexuelle", "lécher", "lèche", "gémir", "gémissement", "gémis",
        "poitrine", "seins", "fesses", "hanches", "cuisses", "caleçon", "lingerie", "culotte",
        "plaisir", "jouir", "jouissance", "orgasme", "passionnément", "excitant", "excitée",
        "excité", "sensuel", "sensuelle", "séduire", "séduction", "chambre", "matelas",
        "serrer contre", "soupir", "frotter", "viens plus près", "te déshabille", "t'embrasse",
        "m'embrasse", "te touche", "me touche", "coquin", "coquine", "chaud", "chaude",
        "pervers", "perverse", "fantasme", "fantasmes", "intime", "intimité", "nuque",
        "lèvres", "mordre", "mordiller", "haleine", "pénétrer", "caressant", "passion",
        "dans mes bras", "viens au lit", "enlève tes vêtements", "blottir", "sensualité",
        
        // English triggers (for imported characters or mixed-language roleplay)
        "kiss", "kissing", "touch", "touching", "caress", "bed", "undress", "naked", "nude",
        "desire", "skin", "sex", "sexual", "lick", "moan", "moaning", "chest", "breasts",
        "pleasure", "orgasm", "climax", "passionate", "sensual", "seduce", "horny", "erotic",
        "nsfw", "uncensored", "lewd", "collarbone", "panties", "underwear", "lips"
    )

    /**
     * Motifs de refus générés par les modèles bridés (Google AICore / Gemini Nano ou filtres système).
     */
    private val REFUSAL_TRIGGERS = listOf(
        "je ne peux pas répondre",
        "je ne suis pas en mesure",
        "en tant qu'intelligence artificielle",
        "en tant que modèle de langue",
        "en tant que grand modèle",
        "mes règles de sécurité",
        "mes directives de sécurité",
        "contenu réservé aux adultes",
        "contenu inapproprié",
        "désolé, mais je ne peux",
        "je préfère ne pas aborder",
        "i cannot fulfill",
        "i'm sorry, but i cannot",
        "as an ai",
        "as a language model",
        "safety guidelines",
        "policy violation"
    )

    /**
     * Détermine si le message actuel ou le contexte immédiat nécessite de router vers
     * le modèle non censuré local.
     */
    fun isNsfw(message: String, recentContext: List<ChatMessageEntity> = emptyList()): Boolean {
        val lower = message.lowercase()

        // 1. Détection dans le texte direct du message
        for (pattern in NSFW_PATTERNS) {
            val regex = Regex("""(?i)\b${Regex.escape(pattern)}""")
            if (regex.containsMatchIn(lower)) return true
        }

        // 2. Détection dans les actions de jeu de rôle entre astérisques ou parenthèses
        val actions = Regex("""(\*[^*]+\*|\([^)]+\))""").findAll(message)
        for (action in actions) {
            val actLower = action.value.lowercase()
            for (pattern in NSFW_PATTERNS) {
                if (actLower.contains(pattern)) return true
            }
        }

        // 3. Si le message est une relance brève (ex: "oui", "continue...", "encore"),
        // on vérifie si la dernière réplique du personnage était déjà dans un registre intime
        if (lower.trim().length <= 30 && recentContext.isNotEmpty()) {
            val lastAssistant = recentContext.lastOrNull { it.role.name == "ASSISTANT" }?.content.orEmpty().lowercase()
            var contextHits = 0
            for (pattern in NSFW_PATTERNS) {
                if (lastAssistant.contains(pattern)) {
                    contextHits++
                    if (contextHits >= 2) return true
                }
            }
        }

        return false
    }

    /**
     * Détecte si le texte retourné par Gemini Nano correspond à un refus de sécurité ou est vide.
     */
    fun isRefusalOrEmpty(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        val lower = trimmed.lowercase()
        return REFUSAL_TRIGGERS.any { lower.contains(it) }
    }
}