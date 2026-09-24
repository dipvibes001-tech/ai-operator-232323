package com.aioperator.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aioperator.ai.AnthropicProvider
import com.aioperator.ai.ChatMessage
import com.aioperator.data.AppDatabase
import com.aioperator.data.PreferencesRepository
import com.aioperator.executor.ToolExecutor
import com.aioperator.model.ExecutionLog
import com.aioperator.model.ToolCall
import com.aioperator.model.ToolResult
import com.aioperator.service.AIOperatorAccessibilityService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiMessage(
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val prefs = PreferencesRepository(application)
    private val executor = ToolExecutor(application)

    val apiKey = prefs.apiKeyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val logs = db.executionLogDao().getAllLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val isAccessibilityEnabled = AIOperatorAccessibilityService.isServiceActive

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            prefs.saveApiKey(key)
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        val currentKey = apiKey.value
        if (currentKey.isBlank()) {
            _messages.update { it + UiMessage("System", "Please set your Anthropic API Key in Settings.") }
            return
        }

        _messages.update { it + UiMessage("User", userText) }
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val provider = AnthropicProvider(currentKey)
                val screenState = AIOperatorAccessibilityService.instance?.captureCurrentScreen()
                val history = _messages.value.takeLast(10).map {
                    ChatMessage(if (it.sender == "User") "user" else "assistant", it.text)
                }

                val aiResponse = provider.generateResponse(userText, history, screenState)
                _messages.update { it + UiMessage("Assistant", aiResponse.message) }

                if (aiResponse.toolCalls.isNotEmpty()) {
                    runTool(userText, aiResponse.toolCalls.first())
                }
            } catch (e: Exception) {
                _messages.update { it + UiMessage("System", "Error: ${e.localizedMessage}") }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun runTool(userCommand: String, toolCall: ToolCall) {
        val result = executor.execute(toolCall)
        val success = result is ToolResult.Success
        val resultText = when (result) {
            is ToolResult.Success -> result.output
            is ToolResult.Failure -> result.error
            is ToolResult.NeedsConfirmation -> "Needs Confirmation"
        }

        db.executionLogDao().insert(
            ExecutionLog(
                userCommand = userCommand,
                toolName = toolCall.tool,
                toolArgs = toolCall.arguments.toString(),
                result = resultText,
                success = success
            )
        )

        _messages.update { it + UiMessage("System", "Tool [${toolCall.tool}]: $resultText") }
    }

    fun clearLogs() {
        viewModelScope.launch {
            db.executionLogDao().clearLogs()
        }
    }
}