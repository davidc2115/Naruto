package com.opencompanion.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité de mémoire vectorielle locale pour chaque personnage.
 * Stocke les souvenirs, interactions clés, jalons intimes, tenues, lieux et dynamiques relationnelles
 * sous forme de vecteur dense (embedding numérique) et de tags catégorisés pour recherche sémantique (RAG).
 */
@Entity(
    tableName = "vector_memories",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("characterId"),
        Index("category"),
        Index("timestamp"),
    ],
)
data class VectorMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: Long,
    val content: String,
    val role: MessageRole? = null,
    val tags: String = "", // Liste de tags séparés par des virgules (ex: "#lingerie, #chambre, #intimite")
    val category: String = "DIALOGUE", // "DIALOGUE", "MILESTONE", "OUTFIT", "LOCATION", "POSTURE", "FACT", "PREFERENCE"
    val vectorBlob: ByteArray, // Vecteur dense d'embeddings normalisé (FloatArray sérialisé en octets)
    val importance: Float = 1.0f, // Score d'importance émotionnelle ou narrative (1.0f à 3.0f)
    val timestamp: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VectorMemoryEntity

        if (id != other.id) return false
        if (characterId != other.characterId) return false
        if (content != other.content) return false
        if (role != other.role) return false
        if (tags != other.tags) return false
        if (category != other.category) return false
        if (!vectorBlob.contentEquals(other.vectorBlob)) return false
        if (importance != other.importance) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + characterId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (role?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + vectorBlob.contentHashCode()
        result = 31 * result + importance.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
