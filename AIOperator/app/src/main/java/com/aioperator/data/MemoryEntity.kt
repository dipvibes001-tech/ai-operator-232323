package com.aioperator.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "zoya_memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,         // जैसे: "bike_number", "key_location"
    val content: String,     // जैसे: "मेरी बाइक का नंबर RJ14-XX है"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Query("SELECT * FROM zoya_memories WHERE key LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("SELECT * FROM zoya_memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("DELETE FROM zoya_memories WHERE key LIKE '%' || :query || '%'")
    suspend fun deleteMemory(query: String)
}
