package com.opencompanion.app.memory.vector

/**
 * Extracteur et classificateur automatique de TAGS pour la mémoire de conversation.
 * Permet d'indexer et d'organiser les souvenirs par thématiques clés, favorisant une recherche
 * hybride (Tags + Vecteurs d'embeddings).
 */
object MemoryTagExtractor {

    private val TAG_RULES = listOf(
        // Tenues, lingerie & style
        listOf("lingerie", "dentelle", "nuisette", "soutien-gorge", "culotte") to "#lingerie",
        listOf("robe", "décolleté", "moulante", "jupe courte", "talons") to "#robe_sensuelle",
        listOf("peignoir", "satin", "soie") to "#peignoir_soyeux",
        listOf("serviette", "bain", "douche") to "#sortie_bain",
        listOf("nue", "déshabillée", "torse nu", "retire") to "#deshabillage",

        // Lieux & Environnements
        listOf("rooftop", "toit", "immeuble", "vue panoramique") to "#rooftop",
        listOf("chambre", "lit", "draps", "oreiller") to "#chambre",
        listOf("salon", "canapé", "fauteuil") to "#salon",
        listOf("cuisine", "comptoir", "plan de travail") to "#cuisine",
        listOf("salle de bain", "baignoire", "douche") to "#salle_de_bain",
        listOf("bureau", "travail", "porte fermée", "verrou") to "#bureau",
        listOf("voiture", "banquette", "parking") to "#voiture",
        listOf("hôtel", "suite") to "#hotel",
        listOf("balcon", "terrasse") to "#terrasse",

        // Postures & Rapprochement physique
        listOf("levrette", "par derrière", "de dos", "penchée en avant", "cambrée") to "#levrette",
        listOf("califourchon", "sur mes genoux", "sur tes genoux", "au-dessus") to "#califourchon",
        listOf("missionnaire", "sur le dos", "face à lui", "face à elle") to "#face_a_face",
        listOf("contre le mur", "plaquée au mur", "adossée") to "#contre_le_mur",

        // Actes & Jalons intimes
        listOf("baiser", "embrasse", "lèvres", "passionné", "langue") to "#baiser",
        listOf("caresse", "peau", "frisson", "corps", "toucher") to "#caresse",
        listOf("fait l'amour", "intimité", "extase", "orgasme", "plaisir") to "#intimite",
        listOf("avoue", "désir", "attirance", "trouble", "charme") to "#aveu_desir",

        // Dynamiques relationnelles & secrets
        listOf("secret", "personne ne doit savoir", "discret", "caché") to "#secret",
        listOf("interdit", "tabou", "danger", "tromper", "tromperie") to "#relation_interdite",
        listOf("belle-mère", "belle-maman") to "#belle_mere",
        listOf("belle-soeur") to "#belle_soeur",
        listOf("belle-fille") to "#belle_fille",
        listOf("confiance", "complicité", "fidélité", "promesse") to "#confiance",
        listOf("jalousie", "jaloux", "jalouse") to "#jalousie",
        listOf("audace", "confiante", "décomplexée", "assumée") to "#audace",
    )

    /**
     * Analyse un texte et extrait l'ensemble des tags pertinents.
     */
    fun extractTags(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val lower = text.lowercase()
        val tags = mutableSetOf<String>()

        for ((keywords, tag) in TAG_RULES) {
            if (keywords.any { lower.contains(it) }) {
                tags.add(tag)
            }
        }

        // Si aucun tag spécifique n'est trouvé, assigner un tag générique
        if (tags.isEmpty()) {
            tags.add("#conversation")
        }

        return tags
    }

    /**
     * Calcule le score de chevauchement entre les tags d'une requête et ceux d'un souvenir enregistré.
     * Renvoie une valeur entre 0.0 et 1.0 (coefficient de Jaccard / matching).
     */
    fun computeTagOverlap(queryTags: Set<String>, memoryTags: Set<String>): Float {
        if (queryTags.isEmpty() || memoryTags.isEmpty()) return 0.0f
        val common = queryTags.intersect(memoryTags).size
        val total = queryTags.union(memoryTags).size
        return if (total > 0) common.toFloat() / total.toFloat() else 0.0f
    }
}
