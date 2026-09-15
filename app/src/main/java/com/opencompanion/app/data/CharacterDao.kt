package com.opencompanion.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getById(id: Long): CharacterEntity?

    @Query("SELECT * FROM characters WHERE id = :id")
    fun observeById(id: Long): Flow<CharacterEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(character: CharacterEntity): Long

    @Update
    suspend fun update(character: CharacterEntity)

    @Update
    suspend fun updateAll(characters: List<CharacterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(characters: List<CharacterEntity>)

    @Delete
    suspend fun delete(character: CharacterEntity)

    @Delete
    suspend fun deleteAll(characters: List<CharacterEntity>)

    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int

    @Query("SELECT * FROM characters")
    suspend fun getAll(): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CharacterEntity?

    @Query("DELETE FROM characters")
    suspend fun clearAll()
}
