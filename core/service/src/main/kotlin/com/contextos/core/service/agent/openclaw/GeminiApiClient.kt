package com.contextos.core.service.agent.openclaw

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight HTTP client for the Gemini REST API.
 *
 * Uses `HttpURLConnection` to avoid adding a Retrofit/OkHttp dependency to
 * `:core:service`. The existing OkHttp stack lives in `:core:network`, and per
 * the module dependency graph in contracts.md `:core:service` already depends
 * on `:core:network`, but we keep this client self-contained so the agent layer
 * has no transitive coupling to the Maps-oriented Retrofit instance.
 *
 * Configuration is read from environment variables set by `env.sh`:
 *   - `OPENCLAW_API_ENDPOINT` — base URL (e.g. `https://generativelanguage.googleapis.com/v1beta/models`)
 *   - `OPENCLAW_API_KEY`      — Gemini API key
 *
 * @see <a href="https://ai.google.dev/api/rest">Gemini REST API docs</a>
 */
@Singleton
class GeminiApiClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Sends a single-turn prompt to the specified Gemini model and returns the raw text response.
     *
     * @param model  The model name (e.g. `gemini-2.0-flash`, `gemini-2.0-flash-lite`).
     * @param prompt The plain-text prompt to send.
     * @return The text content from the first candidate response.
     * @throws GeminiApiException if the API returns an error or the response is unparseable.
     * @throws IOException if the network request fails.
     */
    suspend fun generate(model: String, prompt: String): String {
        return generateMultiTurn(
            model = model,
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)), role = "user")
            ),
        )
    }

    /**
     * Sends a multi-turn conversation to the specified Gemini model and returns the raw text response.
     *
     * @param model    The model name (e.g. `gemini-2.0-flash`).
     * @param contents The full conversation history as a list of [GeminiContent] turns.
     * @param temperature Optional temperature override (default 0.7 for chat, 0.3 for analysis).
     * @return The text content from the first candidate response.
     * @throws GeminiApiException if the API returns an error or the response is unparseable.
     * @throws IOException if the network request fails.
     */
    suspend fun generateMultiTurn(
        model: String,
        contents: List<GeminiContent>,
        temperature: Float = 0.7f,
        maxOutputTokens: Int = 2048,
    ): String = withContext(Dispatchers.IO) {
        val endpoint = com.contextos.core.service.BuildConfig.OPENCLAW_API_ENDPOINT.takeIf { it.isNotEmpty() }
            ?: DEFAULT_ENDPOINT
        val apiKey = com.contextos.core.service.BuildConfig.OPENCLAW_API_KEY.takeIf { it.isNotEmpty() }
            ?: throw GeminiApiException("OPENCLAW_API_KEY not set. Add to local.properties")

        val url = URL("$endpoint/$model:generateContent?key=$apiKey")
        Log.d(TAG, "POST $url (model=$model, turns=${contents.size})")

        val requestBody = GeminiRequest(
            contents = contents,
            generationConfig = GeminiGenerationConfig(
                temperature = temperature,
                maxOutputTokens = maxOutputTokens,
            )
        )

        val requestJson = json.encodeToString(requestBody)

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
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
                Log.e(TAG, "Gemini API error $responseCode: $errorBody")
                val apiError = runCatching { json.decodeFromString<GeminiErrorEnvelope>(errorBody).error }.getOrNull()
                val detailedMessage = when {
                    apiError?.status == "RESOURCE_EXHAUSTED" -> {
                        val quotaHint = if (errorBody.contains("limit: 0")) {
                            " Gemini project/key has no available quota. Enable billing or use a project with Gemini quota."
                        } else {
                            ""
                        }
                        "Gemini API quota exceeded (HTTP $responseCode).${quotaHint}"
                    }
                    apiError?.message != null -> "Gemini API returned HTTP $responseCode: ${apiError.message}"
                    else -> "Gemini API returned HTTP $responseCode: $errorBody"
                }
                throw GeminiApiException(detailedMessage)
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            Log.d(TAG, "Response received (${responseBody.length} chars)")

            val response = json.decodeFromString<GeminiResponse>(responseBody)
            val text = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?: throw GeminiApiException("Empty response from Gemini API: $responseBody")

            text.trim()
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val DEFAULT_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Gemini REST API DTOs (minimal — only what we need)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String = "user",
)

@Serializable
data class GeminiPart(
    val text: String,
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float = 0.3f,
    val maxOutputTokens: Int = 2048,
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
)

@Serializable
data class GeminiErrorEnvelope(
    val error: GeminiErrorBody,
)

@Serializable
data class GeminiErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)

class GeminiApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
