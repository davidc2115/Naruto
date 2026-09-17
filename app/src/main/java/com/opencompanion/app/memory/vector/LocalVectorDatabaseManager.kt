package com.opencompanion.app.memory.vector

import com.opencompanion.app.data.MessageRole
import com.opencompanion.app.data.VectorMemoryDao
import com.opencompanion.app.data.VectorMemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestionnaire central de la Base de Données Vectorielle Locale.
 * Orchestre l'indexation par embeddings vectoriels et tags, et réalise la recherche sémantique (RAG)
 * pour restituer les souvenirs les plus pertinents au modèle de dialogue.
 */
object LocalVectorDatabaseManager {

    data class ScoredMemory(
        val memory: VectorMemoryEntity,
        val similarityScore: Float,
        val tagScore: Float,
        val compositeScore: Float,
    )

    /**
     * Indexe un nouvel élément (souvenir, tour de dialogue, fait marquant) dans la base vectorielle locale.
     */
    suspend fun indexMemory(
        characterId: Long,
        content: String,
        role: MessageRole? = null,
        category: String = "DIALOGUE",
        importance: Float = 1.0f,
        customTags: Set<String> = emptySet(),
        vectorDao: VectorMemoryDao,
    ) = withContext(Dispatchers.IO) {
        if (content.isBlank()) return@withContext

        val autoTags = MemoryTagExtractor.extractTags(content)
        val allTags = (autoTags + customTags).filter { it.isNotBlank() }.toSet()
        val tagsFormatted = allTags.joinToString(", ")

        val embedding = LocalTextEmbeddingModel.embedText(content)
        val blob = LocalTextEmbeddingModel.toByteArray(embedding)

        val entity = VectorMemoryEntity(
            characterId = characterId,
            content = content.trim(),
            role = role,
            tags = tagsFormatted,
            category = category,
            vectorBlob = blob,
            importance = importance.coerceIn(0.5f, 3.0f),
            timestamp = System.currentTimeMillis(),
        )

        vectorDao.insert(entity)

        // Gestion de la rétention : garder les 150 souvenirs les plus récents/importants par personnage
        val totalCount = vectorDao.count(characterId)
        if (totalCount > 160) {
            vectorDao.deleteOldest(characterId, totalCount - 150)
        }
    }

    /**
     * Recherche sémantique hybride (Similarité Cosinus + Chevauchement de Tags + Poids d'Importance).
     */
    suspend fun queryRelevantMemories(
        characterId: Long,
        queryText: String,
        vectorDao: VectorMemoryDao,
        topK: Int = 4,
        minScore: Float = 0.25f,
    ): List<ScoredMemory> = withContext(Dispatchers.IO) {
        if (queryText.isBlank()) return@withContext emptyList()

        val allMemories = vectorDao.getAllForCharacter(characterId)
        if (allMemories.isEmpty()) return@withContext emptyList()

        val queryVec = LocalTextEmbeddingModel.embedText(queryText)
        val queryTags = MemoryTagExtractor.extractTags(queryText)

        val scored = allMemories.mapNotNull { mem ->
            val memVec = LocalTextEmbeddingModel.fromByteArray(mem.vectorBlob)
            val cosSim = LocalTextEmbeddingModel.cosineSimilarity(queryVec, memVec)

            val memTags = mem.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            val tagMatch = MemoryTagExtractor.computeTagOverlap(queryTags, memTags)

            // Formule hybride : 65% proximité sémantique vectorielle, 25% tags, 10% importance narrative
            val normalizedImportance = (mem.importance / 3.0f).coerceIn(0.0f, 1.0f)
            val composite = (0.65f * cosSim) + (0.25f * tagMatch) + (0.10f * normalizedImportance)

            if (composite >= minScore || cosSim >= 0.40f || tagMatch >= 0.50f) {
                ScoredMemory(
                    memory = mem,
                    similarityScore = cosSim,
                    tagScore = tagMatch,
                    compositeScore = composite,
                )
            } else {
                null
            }
        }

        scored.sortedByDescending { it.compositeScore }.take(topK)
    }

    /**
     * Formate les souvenirs vectoriels extraits pour injection dans le prompt système du modèle.
     */
    fun formatMemoriesForPrompt(memories: List<ScoredMemory>): String {
        if (memories.isEmpty()) return ""

        return buildString {
            append("### 🧠 SOUVENIRS ET CONTEXTE RETROUVÉS (BASE VECTORIELLE LOCALE & TAGS) :\n")
            append("Les faits suivants sont issus de votre historique réel partagé. Prends-les impérativement en compte :\n")
            for (item in memories) {
                val tagsStr = if (item.memory.tags.isNotBlank()) "[Tags: ${item.memory.tags}] " else ""
                val categoryPrefix = when (item.memory.category) {
                    "MILESTONE" -> "(Jalon intime passé) "
                    "FACT" -> "(Fait marquant vérifié) "
                    "OUTFIT" -> "(Détail tenue) "
                    "LOCATION" -> "(Lieu & cadre) "
                    else -> "(Échange passé) "
                }
                append("• $tagsStr$categoryPrefix${item.memory.content}\n")
            }
            append("(Consigne : Intègre ces éléments avec fluidité et cohérence, sans jamais te contredire ni feindre l'oubli).\n")
        }.trim()
    }
}
