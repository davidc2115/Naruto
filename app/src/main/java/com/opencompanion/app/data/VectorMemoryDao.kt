package com.opencompanion.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VectorMemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VectorMemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<VectorMemoryEntity>)

    @Query("SELECT * FROM vector_memories WHERE characterId = :characterId ORDER BY timestamp DESC")
    suspend fun getAllForCharacter(characterId: Long): List<VectorMemoryEntity>

    @Query("SELECT * FROM vector_memories WHERE characterId = :characterId AND category = :category ORDER BY timestamp DESC")
    suspend fun getByCategory(characterId: Long, category: String): List<VectorMemoryEntity>

    @Query("SELECT * FROM vector_memories WHERE characterId = :characterId AND tags LIKE '%' || :tag || '%' ORDER BY timestamp DESC")
    suspend fun searchByTag(characterId: Long, tag: String): List<VectorMemoryEntity>

    @Query("SELECT COUNT(*) FROM vector_memories WHERE characterId = :characterId")
    suspend fun count(characterId: Long): Int

    @Query("DELETE FROM vector_memories WHERE id IN (SELECT id FROM vector_memories WHERE characterId = :characterId ORDER BY timestamp ASC LIMIT :limit)")
    suspend fun deleteOldest(characterId: Long, limit: Int)

    @Query("DELETE FROM vector_memories WHERE characterId = :characterId")
    suspend fun clearForCharacter(characterId: Long)
}
