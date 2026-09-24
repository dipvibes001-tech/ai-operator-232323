package com.aioperator.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import java.util.Locale

data class UiMessage(
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

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

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var tts: TextToSpeech? = TextToSpeech(application, this)
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        setupSpeechRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            val voices = tts?.voices
            val femaleVoice = voices?.firstOrNull { 
                it.name.contains("female", ignoreCase = true) || it.name.contains("#female", ignoreCase = true) 
            }
            if (femaleVoice != null) {
                tts?.voice = femaleVoice
            }
        }
    }

    private fun speakOut(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(getApplication())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplication()).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { _isListening.value = false }
                    override fun onError(error: Int) { _isListening.value = false }
                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            sendMessage(matches[0])
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
        _isListening.value = true
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

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
            speakOut("कृपया सेटिंग्स में एपीआई की दर्ज करें")
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
                
                speakOut(aiResponse.message)

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

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
