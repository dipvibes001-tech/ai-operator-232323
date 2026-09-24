package com.aioperator.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aioperator.model.ExecutionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionLogDao {
    @Insert
    suspend fun insert(log: ExecutionLog): Long

    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ExecutionLog>>

    @Query("DELETE FROM execution_logs")
    suspend fun clearLogs()
}