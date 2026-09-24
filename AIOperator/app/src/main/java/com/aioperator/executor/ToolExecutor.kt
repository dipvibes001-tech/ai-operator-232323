package com.aioperator.executor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import com.aioperator.data.AppDatabase
import com.aioperator.data.MemoryEntity
import com.aioperator.model.ToolCall
import com.aioperator.model.ToolResult
import com.aioperator.service.AIOperatorAccessibilityService
import kotlinx.coroutines.delay
import java.net.URLEncoder

class ToolExecutor(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    suspend fun execute(toolCall: ToolCall): ToolResult {
        val service = AIOperatorAccessibilityService.instance

        return when (toolCall.tool) {
            "open_app" -> {
                val appName = toolCall.arguments["app_name"]
                    ?: return ToolResult.Failure("Missing app_name")
                val pm = context.packageManager
                val intent = pm.getInstalledApplications(0).firstOrNull {
                    pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
                }?.let { pm.getLaunchIntentForPackage(it.packageName) }

                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    ToolResult.Success("Launched $appName")
                } else {
                    ToolResult.Failure("App '$appName' not found")
                }
            }

            "tap_text" -> {
                if (service == null) return ToolResult.Failure("Accessibility Service not enabled")
                val text = toolCall.arguments["text"] ?: return ToolResult.Failure("Missing text")
                val success = service.tapOnText(text)
                if (success) ToolResult.Success("Tapped text: $text") else ToolResult.Failure("Text not clickable: $text")
            }

            "type_text" -> {
                if (service == null) return ToolResult.Failure("Accessibility Service not enabled")
                val text = toolCall.arguments["text"] ?: return ToolResult.Failure("Missing text")
                val success = service.typeText(text)
                if (success) ToolResult.Success("Typed text") else ToolResult.Failure("No editable field focused")
            }

            "press_back" -> {
                if (service == null) return ToolResult.Failure("Accessibility Service not enabled")
                service.pressBackAction()
                ToolResult.Success("Pressed Back")
            }

            "press_home" -> {
                if (service == null) return ToolResult.Failure("Accessibility Service not enabled")
                service.pressHomeAction()
                ToolResult.Success("Pressed Home")
            }

            "scroll" -> {
                if (service == null) return ToolResult.Failure("Accessibility Service not enabled")
                val dir = toolCall.arguments["direction"] ?: "down"
                val success = service.scroll(dir)
                if (success) ToolResult.Success("Scrolled $dir") else ToolResult.Failure("Failed to scroll")
            }

            "wait" -> {
                val ms = toolCall.arguments["milliseconds"]?.toLongOrNull() ?: 1000L
                delay(ms)
                ToolResult.Success("Waited ${ms}ms")
            }

            "read_screen" -> {
                if (service == null) return ToolResult.Failure("Accessibility Service not enabled")
                val screen = service.captureCurrentScreen()
                ToolResult.Success("Found ${screen.elements.size} elements")
            }

            // --- हार्डवेयर एवं डिवाइस कंट्रोल्स ---

            "toggle_torch" -> {
                val state = toolCall.arguments["state"] ?: "on"
                try {
                    val cameraId = cameraManager?.cameraIdList?.firstOrNull() 
                        ?: return ToolResult.Failure("फ़ोन में फ़्लैशलाइट नहीं मिली")
                    cameraManager.setTorchMode(cameraId, state.equals("on", ignoreCase = true))
                    ToolResult.Success("टॉर्च $state कर दी गई है")
                } catch (e: Exception) {
                    ToolResult.Failure("Torch Error: ${e.localizedMessage}")
                }
            }

            "adjust_volume" -> {
                val direction = toolCall.arguments["direction"] ?: "up"
                val adjust = if (direction.equals("up", ignoreCase = true)) {
                    AudioManager.ADJUST_RAISE
                } else {
                    AudioManager.ADJUST_LOWER
                }
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, AudioManager.FLAG_SHOW_UI)
                ToolResult.Success("वॉल्यूम $direction कर दिया गया")
            }

            "get_battery" -> {
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                    context.registerReceiver(null, filter)
                }
                val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
                ToolResult.Success("फ़ोन में बैटरी $batteryPct% है")
            }

            // --- व्हाट्सएप और कॉलिंग शॉर्टकट्स ---

            "send_whatsapp" -> {
                val phone = toolCall.arguments["phone"] ?: ""
                val message = toolCall.arguments["message"] ?: ""
                try {
                    val url = if (phone.isNotBlank()) {
                        val cleanPhone = phone.replace("+", "").replace(" ", "")
                        "https://api.whatsapp.com/send?phone=$cleanPhone&text=${URLEncoder.encode(message, "UTF-8")}"
                    } else {
                        "https://api.whatsapp.com/send?text=${URLEncoder.encode(message, "UTF-8")}"
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setPackage("com.whatsapp")
                    }
                    context.startActivity(intent)
                    ToolResult.Success("व्हाट्सएप पर मैसेज भेज दिया गया")
                } catch (e: Exception) {
                    ToolResult.Failure("WhatsApp Error: ${e.localizedMessage}")
                }
            }

            "make_call" -> {
                val phone = toolCall.arguments["phone"] ?: return ToolResult.Failure("फ़ोन नंबर नहीं मिला")
                try {
                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult.Success("$phone पर कॉल मिला दी गई")
                } catch (e: Exception) {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                    ToolResult.Success("डायलर खोल दिया गया: $phone")
                }
            }

            // --- परमानेंट मेमोरी (याद रखने और बताने का टूल) ---

            "remember_info" -> {
                val key = toolCall.arguments["key"] ?: "note"
                val value = toolCall.arguments["value"] ?: ""
                if (value.isBlank()) {
                    ToolResult.Failure("याद रखने के लिए कोई जानकारी नहीं मिली")
                } else {
                    val db = AppDatabase.getDatabase(context)
                    db.memoryDao().insertMemory(
                        MemoryEntity(key = key, content = value)
                    )
                    ToolResult.Success("याद रख लिया: $value")
                }
            }

            "recall_info" -> {
                val query = toolCall.arguments["query"] ?: ""
                val db = AppDatabase.getDatabase(context)
                val results = db.memoryDao().searchMemories(query)
                if (results.isNotEmpty()) {
                    val foundInfo = results.joinToString(", ") { it.content }
                    ToolResult.Success("मुझे यह याद है: $foundInfo")
                } else {
                    ToolResult.Failure("मुझे '$query' के बारे में कोई जानकारी याद नहीं है")
                }
            }

            else -> ToolResult.Failure("Unknown tool: ${toolCall.tool}")
        }
    }
}
