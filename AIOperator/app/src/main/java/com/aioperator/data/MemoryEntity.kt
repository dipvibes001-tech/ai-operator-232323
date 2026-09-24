package com.aioperator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zoya_memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
