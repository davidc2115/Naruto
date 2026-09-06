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

    enum class Tier { RAPIDE, QUALITE }

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
        Entry(
            displayName = "Llama 3.2 3B Uncensored (Q4_K_M)",
            tier = Tier.QUALITE,
            approxSizeGb = 2.24,
            paramCount = "3,2 Md de paramètres",
            license = "Llama 3.2 Community License",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-uncensored-GGUF/resolve/main/Llama-3.2-3B-Instruct-uncensored-Q4_K_M.gguf?download=true",
            fileName = "Llama-3.2-3B-Instruct-uncensored-Q4_K_M.gguf",
            note = "Modèle débridé sans filtre de sécurité, idéal pour le jeu de rôle adulte / NSFW libre et immersif.",
        ),
        // Bonsai 27B (Q1_0, ternaire) a été essayé puis retiré : signalé en pratique par un
        // utilisateur comme faisant planter l'application sur son téléphone. La vérification
        // faite avant de l'ajouter (architecture "qwen35" et type de tenseur Q1_0 tous deux
        // reconnus par le sous-module llama.cpp embarqué, CPU et Vulkan) portait sur le SUPPORT
        // du format — pas sur les OPÉRATEURS de calcul que son architecture à attention hybride
        // (75 % attention "linéaire" façon state-space, 25 % attention classique) sollicite dans
        // le graphe de calcul : rien ne garantit que chacun de ces opérateurs soit implémenté
        // pour le backend Vulkan (au minimum) sur cette version de llama.cpp, ni que 27 Md de
        // paramètres tiennent en mémoire une fois le cache KV ajouté sur un téléphone donné — un
        // échec sur l'un ou l'autre point plante l'app plutôt que d'échouer proprement
        // (voir docs/VULKAN_NOTES.md : un GGML_ASSERT/abort() interne à llama.cpp ne peut pas
        // être rattrapé côté JNI, contrairement à une exception C++ classique). Voir
        // docs/MODELES_ET_AICORE.md pour le détail de ce qui a été vérifié, et de ce qui manquait.
    )
}
