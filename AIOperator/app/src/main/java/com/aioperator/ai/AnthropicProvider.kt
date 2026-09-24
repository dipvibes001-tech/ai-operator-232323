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

data class AIResponse(val message: String, val toolCalls: List<ToolCall>)

class AnthropicProvider(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val systemPrompt = """
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

        If user asks for an action, reply politely in Hindi and output the tool call on a new line:
        TOOL_CALL:{"tool":"tool_name","arguments":{"key":"value"}}
    """.trimIndent()

    suspend fun generateResponse(
        userPrompt: String,
        history: List<ChatMessage>,
        screenState: String? = null
    ): AIResponse = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        
        when {
            // 1. Google Gemini Key (शुरुआत 'AIza' से)
            trimmedKey.startsWith("AIza") -> callGemini(trimmedKey, userPrompt, history, screenState)
            // 2. Anthropic / Claude Key (शुरुआत 'sk-ant' से)
            trimmedKey.startsWith("sk-ant") -> callAnthropic(trimmedKey, userPrompt, history, screenState)
            // 3. OpenAI / Groq / DeepSeek / Any other Key (सामान्य 'sk-' या अन्य)
            else -> callOpenAICompatible(trimmedKey, userPrompt, history, screenState)
        }
    }

    // Google Gemini API Engine
    private fun callGemini(key: String, prompt: String, history: List<ChatMessage>, screenState: String?): AIResponse {
        val contentsArray = JSONArray()

        val sysUser = JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "System Instruction: $systemPrompt")))
        val sysModel = JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", "Understood. I will act as Zoya.")))
        contentsArray.put(sysUser)
        contentsArray.put(sysModel)

        for (msg in history.takeLast(6)) {
            val role = if (msg.role == "user") "user" else "model"
            contentsArray.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", msg.content))))
        }

        val fullPrompt = if (!screenState.isNullOrBlank()) "Current Screen State:\n$screenState\n\nUser: $prompt" else prompt
        contentsArray.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", fullPrompt))))

        val requestJson = JSONObject().put("contents", contentsArray)

        // ऑटो-फॉलऑफ एंडपॉइंट्स: 1.5-flash, gemini-2.0-flash, gemini-pro
        val urls = listOf(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$key",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$key"
        )

        var lastError = ""
        for (url in urls) {
            try {
                val reqBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url(url).post(reqBody).build()
                val res = client.newCall(req).execute()
                val body = res.body?.string() ?: ""

                if (res.isSuccessful) {
                    val json = JSONObject(body)
                    val reply = json.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")
                        ?.optJSONObject(0)?.optString("text") ?: "माफ़ कीजिए, कोई जवाब नहीं मिला।"
                    return parseResponse(reply)
                } else {
                    lastError = "HTTP ${res.code}: $body"
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Network error"
            }
        }
        throw Exception("Gemini Error: $lastError")
    }

    // Claude / Anthropic Engine
    private fun callAnthropic(key: String, prompt: String, history: List<ChatMessage>, screenState: String?): AIResponse {
        val messagesArray = JSONArray()
        for (msg in history.takeLast(6)) {
            messagesArray.put(JSONObject().put("role", msg.role).put("content", msg.content))
        }
        val fullPrompt = if (!screenState.isNullOrBlank()) "Screen State:\n$screenState\n\nUser: $prompt" else prompt
        messagesArray.put(JSONObject().put("role", "user").put("content", fullPrompt))

        val json = JSONObject().apply {
            put("model", "claude-3-haiku-20240307")
            put("max_tokens", 1000)
            put("system", systemPrompt)
            put("messages", messagesArray)
        }

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val res = client.newCall(req).execute()
        val body = res.body?.string() ?: ""
        if (!res.isSuccessful) throw Exception("Anthropic Error (${res.code}): $body")

        val resJson = JSONObject(body)
        val text = resJson.getJSONArray("content").getJSONObject(0).getString("text")
        return parseResponse(text)
    }

    // Universal OpenAI Compatible (OpenAI / Groq / OpenRouter / DeepSeek)
    private fun callOpenAICompatible(key: String, prompt: String, history: List<ChatMessage>, screenState: String?): AIResponse {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))

        for (msg in history.takeLast(6)) {
            messages.put(JSONObject().put("role", msg.role).put("content", msg.content))
        }
        val fullPrompt = if (!screenState.isNullOrBlank()) "Screen State:\n$screenState\n\nUser: $prompt" else prompt
        messages.put(JSONObject().put("role", "user").put("content", fullPrompt))

        val model = if (key.startsWith("gsk_")) "llama-3.3-70b-versatile" else "gpt-4o-mini"
        val endpoint = if (key.startsWith("gsk_")) "https://api.groq.com/openai/v1/chat/completions" else "https://api.openai.com/v1/chat/completions"

        val json = JSONObject().apply {
            put("model", model)
            put("messages", messages)
        }

        val req = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val res = client.newCall(req).execute()
        val body = res.body?.string() ?: ""
        if (!res.isSuccessful) throw Exception("AI API Error (${res.code}): $body")

        val resJson = JSONObject(body)
        val reply = resJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        return parseResponse(reply)
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
                    val args = mutableMapOf<String, String>()
                    argsObj.keys().forEach { key ->
                        args[key] = argsObj.get(key).toString()
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
