package com.aioperator.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class ToolCall(
    val tool: String,
    val arguments: Map<String, String> = emptyMap()
)

@Serializable
data class AIResponse(
    val message: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val confirmationMessage: String? = null
)

data class ScreenElement(
    val text: String,
    val clickable: Boolean,
    val editable: Boolean,
    val resourceId: String? = null,
    val bounds: String? = null
)

data class ScreenState(
    val appPackage: String?,
    val elements: List<ScreenElement>
)

sealed class ToolResult {
    data class Success(val output: String) : ToolResult()
    data class Failure(val error: String) : ToolResult()
    data class NeedsConfirmation(
        val message: String,
        val toolCall: ToolCall
    ) : ToolResult()
}

@Entity(tableName = "execution_logs")
data class ExecutionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userCommand: String,
    val toolName: String,
    val toolArgs: String,
    val result: String,
    val success: Boolean
)