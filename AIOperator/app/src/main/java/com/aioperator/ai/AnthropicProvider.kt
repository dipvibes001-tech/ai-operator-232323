package com.aioperator.ai

import com.aioperator.model.AIResponse
import com.aioperator.model.ScreenState
import com.aioperator.model.ToolCall
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AnthropicProvider(private val apiKey: String) : AIProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val systemPrompt = """
        You are Zoya, a smart, polite, and helpful female AI assistant for Android phones.
        You speak in natural, friendly Hindi/Hinglish.
        Always write a concise, pleasant spoken explanation first (which will be spoken out loud via TTS), 
        followed by a TOOL_CALL if an action is required.

        Available tools:
        - open_app (arguments: {"app_name": "string"})
        - tap_text (arguments: {"text": "string"})
        - type_text (arguments: {"text": "string"})
        - press_back
        - press_home
        - scroll (arguments: {"direction": "up" | "down"})
        - read_screen
        - wait (arguments: {"milliseconds": "1000"})
        - toggle_torch (arguments: {"state": "on" | "off"})
        - adjust_volume (arguments: {"direction": "up" | "down"})
        - get_battery
        - send_whatsapp (arguments: {"phone": "string optional", "message": "string"})
        - make_call (arguments: {"phone": "string"})
        - remember_info (arguments: {"key": "string", "value": "string"})
        - recall_info (arguments: {"query": "string"})

        Format:
        Explanation here (friendly Hindi/Hinglish).
        TOOL_CALL:{"tool":"open_app","arguments":{"app_name":"YouTube"}}
    """.trimIndent()

    override suspend fun generateResponse(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        currentScreen: ScreenState?
    ): AIResponse {

        val screenContext = currentScreen?.let { screen ->
            buildString {
                append("\n\nCurrent screen — App: ${screen.appPackage ?: "unknown"}")
                append("\nVisible elements:")
                screen.elements.take(15).forEach { e ->
                    append("\n  • \"${e.text}\"")
                }
            }
        } ?: ""

        val messages = buildJsonArray {
            conversationHistory.forEach { msg ->
                addJsonObject {
                    put("role", msg.role)
                    put("content", msg.content)
                }
            }
            addJsonObject {
                put("role", "user")
                put("content", userMessage + screenContext)
            }
        }

        val requestBody = buildJsonObject {
            put("model", "claude-3-5-sonnet-20241022")
            put("max_tokens", 1024)
            put("system", systemPrompt)
            put("messages", messages)
        }.toString()

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return AIResponse(message = "Empty response")
            if (!response.isSuccessful) return AIResponse(message = "API error ${response.code}: $body")
            parseResponse(body)
        } catch (e: Exception) {
            AIResponse(message = "Network error: ${e.message}")
        }
    }

    private fun parseResponse(raw: String): AIResponse {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val text = root["content"]?.jsonArray
                ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return AIResponse(message = "No response")

            val lines = text.lines()
            val toolIndex = lines.indexOfFirst { it.trim().startsWith("TOOL_CALL:") }

            if (toolIndex < 0) {
                return AIResponse(message = text)
            }

            val message = lines.take(toolIndex).joinToString("\n").trim()
            val toolJson = lines[toolIndex].trim().removePrefix("TOOL_CALL:").trim()
            val toolObj = json.parseToJsonElement(toolJson).jsonObject

            val toolCall = ToolCall(
                tool = toolObj["tool"]?.jsonPrimitive?.content ?: "",
                arguments = toolObj["arguments"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
            )

            AIResponse(message = message, toolCalls = listOf(toolCall))
        } catch (e: Exception) {
            AIResponse(message = "Parse error: ${e.message}")
        }
    }

    override suspend fun summarizeForMemory(text: String): String = text
}
