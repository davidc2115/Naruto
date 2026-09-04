package com.opencompanion.app.engine

/**
 * Préréglages de modèles GGUF proposés en un tap dans Réglages → Modèle, en plus de l'import
 * libre par fichier/URL déjà offert par [ModelManager]. Choix et tailles vérifiés en
 * septembre 2026 (voir docs/MODELES_ET_AICORE.md pour les sources) ; comme pour tout import,
 * ce sont de simples téléchargements HTTP directs, sans clé ni compte.
 *
 * Les URLs pointent vers des dépôts publics Hugging Face (fichiers statiques, accès anonyme) —
 * ce n'est PAS une dépendance à l'API ou au compte HuggingFace, seulement l'hébergeur qui
 * publie le plus largement des quantifications GGUF prêtes à l'emploi. Rien n'empêche d'utiliser
 * l'import par URL pour pointer ailleurs.
 */
object RecommendedModels {

    enum class Tier { RAPIDE, QUALITE, ENORME }

    data class Entry(
        val displayName: String,
        val tier: Tier,
        val approxSizeGb: Double,
        val paramCount: String,
        val license: String,
        val downloadUrl: String,
        val fileName: String,
        val note: String,
    )

    val ALL: List<Entry> = listOf(
        Entry(
            displayName = "Qwen3 0.6B (Q4_K_M)",
            tier = Tier.RAPIDE,
            approxSizeGb = 0.40,
            paramCount = "0,6 Md de paramètres",
            license = "Apache 2.0",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf?download=true",
            fileName = "Qwen3-0.6B-Q4_K_M.gguf",
            note = "Le plus léger et le plus rapide, même en CPU pur. Bien pour du chat court et simple ; ne pas en attendre de raisonnement complexe.",
        ),
        Entry(
            displayName = "Gemma 3 1B (Q4_K_M)",
            tier = Tier.RAPIDE,
            approxSizeGb = 0.81,
            paramCount = "1 Md de paramètres",
            license = "Gemma (Google)",
            downloadUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf?download=true",
            fileName = "gemma-3-1b-it-Q4_K_M.gguf",
            note = "Très bon support multilingue (140+ langues) pour sa taille, réponses rapides.",
        ),
        Entry(
            displayName = "Llama 3.2 1B (Q4_K_M)",
            tier = Tier.RAPIDE,
            approxSizeGb = 0.81,
            paramCount = "1 Md de paramètres",
            license = "Llama 3.2 Community License",
            downloadUrl = "https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf?download=true",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            note = "Généraliste rapide, bon compromis vitesse/qualité pour du chat courant.",
        ),
        Entry(
            displayName = "Phi-4-mini (Q4_K_M)",
            tier = Tier.QUALITE,
            approxSizeGb = 2.49,
            paramCount = "3,8 Md de paramètres",
            license = "MIT",
            downloadUrl = "https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf?download=true",
            fileName = "Phi-4-mini-instruct-Q4_K_M.gguf",
            note = "Très bon raisonnement pour sa taille, fenêtre de contexte large (128K). Plus lent sans GPU stable.",
        ),
        Entry(
            displayName = "Qwen3 4B (Q4_K_M)",
            tier = Tier.QUALITE,
            approxSizeGb = 2.50,
            paramCount = "4 Md de paramètres",
            license = "Apache 2.0",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf?download=true",
            fileName = "Qwen3-4B-Q4_K_M.gguf",
            note = "Mode « réflexion » activable, bonnes performances générales. Plus lent sans GPU stable.",
        ),
        Entry(
            displayName = "Gemma 3 4B (Q4_K_M)",
            tier = Tier.QUALITE,
            approxSizeGb = 2.49,
            paramCount = "4 Md de paramètres",
            license = "Gemma (Google)",
            downloadUrl = "https://huggingface.co/unsloth/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf?download=true",
            fileName = "gemma-3-4b-it-Q4_K_M.gguf",
            note = "Meilleur choix qualité/multilingue de cette liste. Plus lent sans GPU stable.",
        ),
        // Bonsai 27B : 27 Md de paramètres compressés en quantification "ternaire" native
        // (Q1_0, ~1,1 bit/poids au lieu des ~4 bits habituels), ce qui explique une taille de
        // fichier comparable à un modèle 4 Md classique malgré un nombre de paramètres bien plus
        // élevé. Vérifié : le sous-module llama.cpp embarqué (voir external/llama.cpp) reconnaît
        // déjà l'architecture "qwen35" et le type de tenseur Q1_0 (CPU et Vulkan), donc ce modèle
        // se charge avec le moteur existant, sans fork ni build spécial.
        //
        // ATTENTION vitesse : la quantification 1 bit réduit la taille du FICHIER, pas le nombre
        // de calculs par token — à 27 Md de paramètres, chaque réponse reste bien plus lourde à
        // calculer qu'avec les modèles 1-4 Md de cette liste, quelle que soit la quantification.
        // Le réglage « Couches déchargées sur le GPU » (Réglages → Matériel) a un défaut de 20,
        // pensé pour ces petits modèles (20-36 couches au total) : sur Bonsai 27B, qui a beaucoup
        // plus de couches, 20 ne représente qu'une fraction du modèle et laisse l'essentiel sur
        // CPU — remonter cette valeur (vers 999, ou par paliers si la VRAM ne suit pas) est le
        // levier le plus direct pour accélérer ce modèle en particulier.
        Entry(
            displayName = "Bonsai 27B (Q1_0, ternaire)",
            tier = Tier.ENORME,
            approxSizeGb = 3.80,
            paramCount = "27 Md de paramètres (quantifiés en ~1,1 bit/poids)",
            license = "Apache 2.0",
            downloadUrl = "https://huggingface.co/prism-ml/Bonsai-27B-gguf/resolve/main/Bonsai-27B-Q1_0.gguf?download=true",
            fileName = "Bonsai-27B-Q1_0.gguf",
            note = "Modèle expérimental bien plus gros que les autres de cette liste (27 Md de " +
                "paramètres) : la quantification 1 bit réduit la taille du fichier, pas le temps " +
                "de calcul par réponse — reste bien plus lent que les modèles 1-4 Md ci-dessus, " +
                "même avec le GPU. Pense à remonter « Couches déchargées sur le GPU » (Réglages " +
                "→ Matériel, 20 par défaut) vers 999 pour ce modèle précisément, sinon la plupart " +
                "de ses couches tournent sur CPU.",
        ),
    )
}
