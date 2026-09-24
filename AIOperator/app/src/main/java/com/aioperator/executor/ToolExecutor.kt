package com.aioperator.executor

import android.content.Context
import android.content.Intent
import com.aioperator.model.ToolCall
import com.aioperator.model.ToolResult
import com.aioperator.service.AIOperatorAccessibilityService
import kotlinx.coroutines.delay

class ToolExecutor(private val context: Context) {

    suspend fun execute(toolCall: ToolCall): ToolResult {
        val service = AIOperatorAccessibilityService.instance

        return when (toolCall.tool) {
            "open_app" -> {
                val appName = toolCall.arguments["app_name"]
                    ?: return ToolResult.Failure("Missing app_name")
                val pm = context.packageManager
                val intent = pm.getInstalledApplications(0).firstOrNull {
                    pm.getApplicationLabel(it).toString().equals(appName, ignoreCase = true)
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

            else -> ToolResult.Failure("Unknown tool: ${toolCall.tool}")
        }
    }
}