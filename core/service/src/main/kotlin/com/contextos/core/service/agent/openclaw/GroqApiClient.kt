package com.contextos.core.service.agent.openclaw

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqApiClient @Inject constructor() {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun generateMultiTurn(
        model: String,
        messages: List<GroqMessage>,
        temperature: Float = 0.7f,
        maxTokens: Int = 2048,
    ): String = withContext(Dispatchers.IO) {
        val (text, _) = postChat(model, messages, tools = null, temperature, maxTokens)
        text
    }

    /**
     * Sends a chat request with tool definitions. Returns the raw assistant
     * response message (may contain tool_calls instead of text).
     */
    suspend fun generateWithTools(
        model: String,
        messages: List<GroqMessage>,
        tools: List<GroqToolDefinition>,
        temperature: Float = 0.7f,
        maxTokens: Int = 2048,
    ): GroqMessage = withContext(Dispatchers.IO) {
        val (text, msg) = postChat(model, messages, tools, temperature, maxTokens)
        GroqMessage(role = "assistant", content = msg.content, toolCalls = msg.toolCalls)
    }

    /** Shared POST logic. Returns (text, raw response message). */
    private suspend fun postChat(
        model: String,
        messages: List<GroqMessage>,
        tools: List<GroqToolDefinition>?,
        temperature: Float,
        maxTokens: Int,
    ): Pair<String, GroqMessageResponse> = withContext(Dispatchers.IO) {
        val apiKey = com.contextos.core.service.BuildConfig.OPENCLAW_API_KEY.takeIf { it.isNotEmpty() }
            ?: throw GroqApiException("OPENCLAW_API_KEY not set")

        val url = URL("https://api.groq.com/openai/v1/chat/completions")
        Log.d(TAG, "POST $url (model=$model, messages=${messages.size}, tools=${tools?.size ?: 0})")

        val requestBody = GroqRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            max_tokens = maxTokens,
            tools = tools,
            toolChoice = if (tools != null) "auto" else null,
        )

        val requestJson = json.encodeToString(requestBody)
        Log.d(TAG, "Request JSON (${requestJson.length} chars): ${requestJson.take(2000)}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }

        try {
            connection.outputStream.use { os ->
                os.write(requestJson.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "no body"
                Log.e(TAG, "Groq API error $responseCode: $errorBody")
                throw GroqApiException("Groq API returned HTTP $responseCode: $errorBody")
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            Log.d(TAG, "Response received (${responseBody.length} chars)")

            val response = json.decodeFromString<GroqResponse>(responseBody)
            val msg = response.choices?.firstOrNull()?.message
                ?: throw GroqApiException("Empty response from Groq API")

            val text = msg.content?.trim() ?: ""
            text to msg
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "GroqApiClient"
    }
}

@Serializable
data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Float = 0.7f,
    val max_tokens: Int = 2048,
    val tools: List<GroqToolDefinition>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<GroqToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
)

/** A tool definition the model may call. */
@Serializable
data class GroqToolDefinition(
    val type: String,
    val function: GroqFunctionDefinition,
)

@Serializable
data class GroqFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonElement,
)

/** A tool-call returned by the model. */
@Serializable
data class GroqToolCall(
    val id: String,
    val type: String,
    val function: GroqFunctionCall,
)

@Serializable
data class GroqFunctionCall(
    val name: String,
    val arguments: String,
)

@Serializable
data class GroqResponse(
    val choices: List<GroqChoice>? = null,
)

@Serializable
data class GroqChoice(
    val message: GroqMessageResponse? = null,
)

@Serializable
data class GroqMessageResponse(
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<GroqToolCall>? = null,
)

class GroqApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
