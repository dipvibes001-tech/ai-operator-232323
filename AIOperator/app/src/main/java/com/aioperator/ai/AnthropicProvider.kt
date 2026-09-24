package com.aioperator.ai

import com.aioperator.model.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)
data class AIResponse(val message: String, val toolCalls: List<ToolCall>)

class AnthropicProvider(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateResponse(
        userPrompt: String,
        history: List<ChatMessage>,
        screenState: String? = null
    ): AIResponse = withContext(Dispatchers.IO) {

        val systemPrompt = """
            You are Zoya, a smart and polite female AI phone assistant.
            You help the user operate their Android phone.
            
            Available Tools:
            - open_app(app_name: string)
            - toggle_torch(state: "on"|"off")
            - adjust_volume(direction: "up"|"down")
            - get_battery()
            - send_whatsapp(phone: string, message: string)
            - make_call(phone: string)
            - tap_text(text: string)
            - type_text(text: string)
            - scroll(direction: "up"|"down")
            - press_back()
            - press_home()
            - remember_info(key: string, value: string)
            - recall_info(query: string)

            If the user asks to perform an action, reply politely in Hindi and output the tool call on a new line formatted exactly like:
            TOOL_CALL:{"tool":"tool_name","arguments":{"key":"value"}}
            
            Example:
            हाँ जी दीप, मैं यूट्यूब खोल रही हूँ।
            TOOL_CALL:{"tool":"open_app","arguments":{"app_name":"YouTube"}}
        """.trimIndent()

        val contentsArray = JSONArray()

        // System Instruction
        val systemPart = JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "System Instruction: $systemPrompt")))
        contentsArray.put(systemPart)
        val systemModelPart = JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", "Understood. I will act as Zoya and execute tools using the exact TOOL_CALL format.")))
        contentsArray.put(systemModelPart)

        // Previous history
        for (msg in history.takeLast(6)) {
            val role = if (msg.role == "user") "user" else "model"
            contentsArray.put(
                JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
            )
        }

        // Current message with screen state
        val promptWithContext = if (!screenState.isNullOrBlank()) {
            "Current Screen State:\n$screenState\n\nUser: $userPrompt"
        } else {
            userPrompt
        }

        contentsArray.put(
            JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", promptWithContext)))
        )

        val requestJson = JSONObject().apply {
            put("contents", contentsArray)
        }

        // Gemini 1.5 Flash endpoint
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errorMsg = try {
                JSONObject(responseBody).getJSONObject("error").getString("message")
            } catch (e: Exception) {
                responseBody
            }
            throw Exception("Gemini Error (${response.code}): $errorMsg")
        }

        val jsonResponse = JSONObject(responseBody)
        val candidates = jsonResponse.optJSONArray("candidates")
        val contentObj = candidates?.optJSONObject(0)?.optJSONObject("content")
        val parts = contentObj?.optJSONArray("parts")
        val rawReply = parts?.optJSONObject(0)?.optString("text") ?: "माफ़ कीजिए, कोई जवाब नहीं मिला।"

        parseResponse(rawReply)
    }

    private fun parseResponse(rawReply: String): AIResponse {
        val toolCalls = mutableListOf<ToolCall>()
        val cleanLines = mutableListOf<String>()

        rawReply.lines().forEach { line ->
            if (line.trim().startsWith("TOOL_CALL:")) {
                try {
                    val jsonStr = line.trim().removePrefix("TOOL_CALL:").trim()
                    val json = JSONObject(jsonStr)
                    val toolName = json.getString("tool")
                    val argsObj = json.optJSONObject("arguments") ?: JSONObject()
                    val args = mutableMapOf<String, Any>()
                    argsObj.keys().forEach { key ->
                        args[key] = argsObj.get(key)
                    }
                    toolCalls.add(ToolCall(toolName, args))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                cleanLines.add(line)
            }
        }

        val displayMessage = cleanLines.joinToString("\n").trim()
        return AIResponse(
            message = if (displayMessage.isBlank()) "काम कर दिया है।" else displayMessage,
            toolCalls = toolCalls
        )
    }
}
