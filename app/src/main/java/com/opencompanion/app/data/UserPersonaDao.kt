package com.opencompanion.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPersonaDao {
    @Query("SELECT * FROM user_personas ORDER BY isDefault DESC, createdAt ASC")
    fun observeAll(): Flow<List<UserPersonaEntity>>

    @Query("SELECT * FROM user_personas ORDER BY isDefault DESC, createdAt ASC")
    suspend fun getAll(): List<UserPersonaEntity>

    @Query("SELECT * FROM user_personas WHERE id = :id")
    suspend fun getById(id: Long): UserPersonaEntity?

    @Query("SELECT * FROM user_personas WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): UserPersonaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(persona: UserPersonaEntity): Long

    @Update
    suspend fun update(persona: UserPersonaEntity)

    @Delete
    suspend fun delete(persona: UserPersonaEntity)

    /** Retire le drapeau par défaut de tous les personas sauf [keepId] — utilisé pour garantir
     *  qu'un seul persona est marqué par défaut à la fois (voir CharacterRepository.setDefaultPersona). */
    @Query("UPDATE user_personas SET isDefault = 0 WHERE id != :keepId")
    suspend fun clearDefaultExcept(keepId: Long)

    @Query("SELECT COUNT(*) FROM user_personas")
    suspend fun count(): Int
}
