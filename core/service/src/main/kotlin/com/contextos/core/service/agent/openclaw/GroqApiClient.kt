package com.contextos.core.service.agent.openclaw

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        val apiKey = com.contextos.core.service.BuildConfig.OPENCLAW_API_KEY.takeIf { it.isNotEmpty() }
            ?: throw GroqApiException("OPENCLAW_API_KEY not set")

        val url = URL("https://api.groq.com/openai/v1/chat/completions")
        Log.d(TAG, "POST $url (model=$model, messages=${messages.size})")

        val requestBody = GroqRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            max_tokens = maxTokens,
        )

        val requestJson = json.encodeToString(requestBody)
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
            val text = response.choices?.firstOrNull()?.message?.content
                ?: throw GroqApiException("Empty response from Groq API")

            text.trim()
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
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: String,
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
)

class GroqApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
