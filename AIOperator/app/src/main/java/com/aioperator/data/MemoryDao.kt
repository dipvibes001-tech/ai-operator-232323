package com.aioperator.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Query(
        """
        SELECT * FROM memories
        WHERE :query = ''
           OR `key` LIKE '%' || :query || '%'
           OR content LIKE '%' || :query || '%'
        ORDER BY id DESC
        """
    )
    suspend fun searchMemories(query: String): List<MemoryEntity>
}
