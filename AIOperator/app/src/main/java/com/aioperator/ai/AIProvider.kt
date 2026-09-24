package com.aioperator.ai

import com.aioperator.model.AIResponse
import com.aioperator.model.ScreenState

data class ChatMessage(
    val role: String,
    val content: String
)

interface AIProvider {
    suspend fun generateResponse(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        currentScreen: ScreenState?
    ): AIResponse

    suspend fun summarizeForMemory(text: String): String
}