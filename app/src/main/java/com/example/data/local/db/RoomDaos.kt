package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHistoryDao {
    @Query("SELECT * FROM chat_history ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSession(session: ChatHistoryEntity)

    @Query("DELETE FROM chat_history WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM chat_history")
    suspend fun clearAll()
}

@Dao
interface UserMemoryVaultDao {
    @Query("SELECT * FROM user_memory_vault ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<UserMemoryVaultEntity>>

    @Query("SELECT * FROM user_memory_vault WHERE key = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): UserMemoryVaultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: UserMemoryVaultEntity)

    @Query("DELETE FROM user_memory_vault WHERE key = :key")
    suspend fun deleteMemory(key: String)

    @Query("DELETE FROM user_memory_vault")
    suspend fun clearAllMemories()
}
