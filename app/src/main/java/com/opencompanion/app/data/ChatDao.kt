package com.opencompanion.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE characterId = :characterId ORDER BY timestamp ASC")
    fun observeMessages(characterId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC")
    fun observeAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE characterId = :characterId ORDER BY timestamp ASC")
    suspend fun getMessages(characterId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE characterId = :characterId")
    suspend fun clearHistory(characterId: Long)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE characterId = :characterId")
    suspend fun countMessagesForCharacter(characterId: Long): Int

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("UPDATE chat_messages SET characterId = :targetId WHERE characterId = :sourceId")
    suspend fun migrateMessages(sourceId: Long, targetId: Long)

    @Query("SELECT DISTINCT characterId FROM chat_messages")
    suspend fun getCharacterIdsWithMessages(): List<Long>
}
