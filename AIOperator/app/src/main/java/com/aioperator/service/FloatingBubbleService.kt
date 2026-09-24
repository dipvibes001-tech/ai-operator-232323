package com.aioperator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.aioperator.ai.AnthropicProvider
import com.aioperator.ai.ChatMessage
import com.aioperator.data.PreferencesRepository
import com.aioperator.executor.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class FloatingBubbleService : Service(), TextToSpeech.OnInitListener {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var isListeningForCommand = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        tts = TextToSpeech(this, this)
        setupFloatingView()
        setupSpeechRecognizer()
        startListening()
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

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ZOYA_TTS")
    }

    private fun startForegroundServiceNotification() {
        val channelId = "zoya_assistant_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Zoya AI Floating Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Zoya AI Assistant Active")
            .setContentText("Listening for 'Hey Zoya' or 'Hey Deep'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(101, notification)
    }

    private fun setupFloatingView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val bubble = TextView(this).apply {
            text = "D"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER

            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(Color.parseColor("#8B5CF6"), Color.parseColor("#38BDF8"))
                orientation = GradientDrawable.Orientation.TL_BR
                setStroke(4, Color.parseColor("#FFFFFF"))
            }
            background = shape
        }
        floatingView = bubble

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            150,
            150,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 250
        }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (Math.abs(event.rawX - initialTouchX) < 15 && Math.abs(event.rawY - initialTouchY) < 15) {
                            activateZoya()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(floatingView, params)
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    startListening()
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        handleHeardSpeech(matches[0])
                    }
                    startListening()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun activateZoya() {
        isListeningForCommand = true
        speak("हाँ जी दीप, बताइए क्या करूँ?")
    }

    private fun handleHeardSpeech(text: String) {
        val lower = text.lowercase()

        if (!isListeningForCommand && (lower.contains("zoya") || lower.contains("जोया") || 
            lower.contains("deep") || lower.contains("दीप"))) {
            activateZoya()
            return
        }

        if (isListeningForCommand) {
            isListeningForCommand = false
            executeUserCommand(text)
        }
    }

    private fun executeUserCommand(command: String) {
        serviceScope.launch {
            val prefs = PreferencesRepository(applicationContext)
            val apiKey = prefs.apiKeyFlow.first()
            if (apiKey.isBlank()) {
                speak("कृपया सेटिंग्स में एपीआई की दर्ज करें")
                return@launch
            }

            try {
                val provider = AnthropicProvider(apiKey)
                // Type mismatch fix: toString() जोड़ा गया है
                val screenState = AIOperatorAccessibilityService.instance?.captureCurrentScreen()?.toString()
                val response = provider.generateResponse(command, listOf(ChatMessage("user", command)), screenState)

                speak(response.message)

                if (response.toolCalls.isNotEmpty()) {
                    val executor = ToolExecutor(applicationContext)
                    executor.execute(response.toolCalls.first())
                }
            } catch (e: Exception) {
                speak("त्रुटि: " + e.localizedMessage)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
