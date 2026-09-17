package com.opencompanion.app.memory.vector

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Modèle local de texte en vecteur (Text-to-Vector Embeddings) 100% autonome, rapide et offline.
 * Génère des vecteurs denses de dimension 256 pour la recherche sémantique (RAG) dans la mémoire
 * des conversations, sans nécessiter de serveur distant ni consommer de quota.
 */
object LocalTextEmbeddingModel {

    const val VECTOR_DIM = 256

    // Ancrages sémantiques clés mappés sur des dimensions spécifiques pour garantir
    // un regroupement sémantique précis des thématiques conversationnelles.
    private val SEMANTIC_ANCHORS = mapOf(
        // Intimité & Sensualité (dims 0..31)
        listOf("baiser", "embrasse", "lèvres", "bouche", "langue", "baisers") to 0,
        listOf("caresse", "caresses", "peau", "corps", "frisson", "doux", "douceur") to 4,
        listOf("lingerie", "dentelle", "nuisette", "soutien-gorge", "culotte", "soie", "satin", "peignoir") to 8,
        listOf("robe", "décolleté", "moulante", "jupe", "talons", "chemise", "déboutonne") to 12,
        listOf("désir", "trouble", "attirance", "séduction", "charme", "sensuelle", "sexy", "provocante") to 16,
        listOf("intimité", "amour", "nuit", "passion", "extase", "orgasme", "plaisir", "jouissance") to 20,
        listOf("seins", "poitrine", "fesses", "hanches", "cuisses", "cambrure", "ventre") to 24,
        listOf("soupir", "gémissement", "chuchotement", "regard", "yeux", "fièvre", "chaleur") to 28,

        // Postures & Géométrie physique (dims 32..63)
        listOf("levrette", "derrière", "dos", "penchée", "cambrée", "tournée") to 32,
        listOf("califourchon", "genoux", "dessus", "assis", "assise", "chevaucher") to 36,
        listOf("face", "yeux", "regard", "missionnaire", "allongée", "dos") to 40,
        listOf("mur", "plaquée", "adossée", "debout", "coin") to 44,
        listOf("mains", "bras", "épaules", "torse", "cou", "nuque", "cheveux") to 48,
        listOf("enlacer", "étreinte", "serrer", "retenir", "guidé", "presser") to 52,

        // Lieux & Environnements (dims 64..95)
        listOf("rooftop", "toit", "immeuble", "ciel", "étoiles", "rambarde", "hauteur") to 64,
        listOf("chambre", "lit", "draps", "oreiller", "couette", "sommier") to 68,
        listOf("salon", "canapé", "fauteuil", "coussin", "télévision", "table") to 72,
        listOf("cuisine", "comptoir", "plan", "travail", "évier", "café") to 76,
        listOf("salle", "bain", "douche", "baignoire", "serviette", "eau", "mousse") to 80,
        listOf("bureau", "travail", "fauteuil", "porte", "clé", "verrou", "dossier") to 84,
        listOf("voiture", "siège", "banquette", "vitres", "nuit", "garage", "parking") to 88,
        listOf("hôtel", "chambre", "suite", "discret", "week-end", "voyage") to 92,

        // Dynamiques relationnelles, secrets & famille (dims 96..127)
        listOf("secret", "discret", "personne", "savoir", "caché", "mystère") to 96,
        listOf("interdit", "tabou", "danger", "risque", "surprendre", "découvrir") to 100,
        listOf("tromper", "tromperie", "mari", "femme", "conjoint", "faute") to 104,
        listOf("belle-mère", "belle-soeur", "belle-fille", "famille", "cousine", "tante") to 108,
        listOf("confiance", "complicité", "pacte", "promesse", "fidélité", "lien") to 112,
        listOf("jalousie", "jaloux", "jalouse", "autre", "rival", "regard") to 116,
        listOf("audace", "assumer", "décomplexée", "confiante", "sans", "peur") to 120,

        // Émotions & Sentiments (dims 128..159)
        listOf("amour", "aime", "adorer", "attachement", "sentiment", "coeur") to 128,
        listOf("joie", "rire", "sourire", "amusement", "plaisanterie", "taquiner") to 132,
        listOf("tendresse", "douceur", "câlin", "apaisement", "chaleureux") to 136,
        listOf("peur", "crainte", "panique", "hésitation", "retenue", "gêne") to 140,
        listOf("surprise", "étonnement", "inattendu", "découverte") to 144,
        listOf("satisfaction", "comblé", "heureux", "reconnaissant", "merci") to 148,
    )

    /**
     * Transforme n'importe quel texte en vecteur dense normalisé de dimension 256.
     */
    fun embedText(text: String): FloatArray {
        val vector = FloatArray(VECTOR_DIM)
        if (text.isBlank()) return vector

        val cleaned = text.lowercase()
        val tokens = cleaned.split(Regex("[\\s,.;:!?()'\"«»\n\r]+")).filter { it.length >= 2 }

        // 1. Projection sémantique par ancrages clés
        for ((words, baseDim) in SEMANTIC_ANCHORS) {
            for (word in words) {
                if (cleaned.contains(word)) {
                    val weight = if (word.length > 4) 1.5f else 1.0f
                    for (offset in 0 until 4) {
                        val dim = (baseDim + offset) % VECTOR_DIM
                        vector[dim] += weight * (1.0f / (offset + 1))
                    }
                }
            }
        }

        // 2. Projection de Hashing n-grammes de sous-mots (pour le vocabulaire libre)
        for (token in tokens) {
            val tokenHash = token.hashCode()
            val dimWord = abs(tokenHash) % VECTOR_DIM
            val signWord = if ((tokenHash and 1) == 0) 1.0f else -1.0f
            vector[dimWord] += signWord * 0.8f

            // 3-grams de sous-mots pour capturer racines et accords
            if (token.length >= 3) {
                for (i in 0..token.length - 3) {
                    val sub = token.substring(i, i + 3)
                    val subHash = sub.hashCode()
                    val dimSub = abs(subHash * 31 + i) % VECTOR_DIM
                    val signSub = if ((subHash and 1) == 0) 0.4f else -0.4f
                    vector[dimSub] += signSub
                }
            }
        }

        // 3. Normalisation L2 (Unit Vector)
        normalizeInPlace(vector)
        return vector
    }

    /**
     * Calcule la similarité cosinus entre deux vecteurs denses (valeur comprise entre -1.0 et 1.0).
     * Deux vecteurs identiques ou très proches sémantiquement renvoient un score > 0.70.
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty()) return 0.0f
        val len = minOf(v1.size, v2.size)
        var dot = 0.0f
        for (i in 0 until len) {
            dot += v1[i] * v2[i]
        }
        return dot.coerceIn(-1.0f, 1.0f)
    }

    /**
     * Normalise le vecteur à une norme euclidienne unitaire (L2 = 1.0).
     */
    fun normalizeInPlace(v: FloatArray) {
        var sumSq = 0.0f
        for (f in v) {
            sumSq += f * f
        }
        val norm = sqrt(sumSq)
        if (norm > 1e-6f) {
            val invNorm = 1.0f / norm
            for (i in v.indices) {
                v[i] *= invNorm
            }
        }
    }

    /**
     * Sérialise un FloatArray en ByteArray pour stockage compact dans SQLite / Room.
     */
    fun toByteArray(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in vector) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    /**
     * Désérialise un ByteArray en FloatArray.
     */
    fun fromByteArray(bytes: ByteArray): FloatArray {
        if (bytes.size < 4) return FloatArray(VECTOR_DIM)
        val count = bytes.size / 4
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val vector = FloatArray(count)
        for (i in 0 until count) {
            vector[i] = buffer.float
        }
        return vector
    }
}
