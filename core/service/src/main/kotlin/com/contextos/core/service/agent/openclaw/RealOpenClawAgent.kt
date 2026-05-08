package com.contextos.core.service.agent.openclaw

import android.util.Log
import com.contextos.core.data.model.ActionRecommendation
import com.contextos.core.data.model.DraftingContext
import com.contextos.core.data.model.SituationAnalysis
import com.contextos.core.data.model.SituationModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [OpenClawAgent] backed by Gemini or Groq REST API.
 *
 * Auto-detects provider based on API key format:
 *   - Groq key (starts with "gsk_") → uses GroqApiClient
 *   - Gemini key → uses GeminiApiClient
 *
 * Uses [OpenClawPromptBuilder] to construct structured prompts.
 *
 * Model selection (from `local.properties`):
 *   - Groq: mixtral-8x7b-32768 (default)
 *   - Gemini: gemini-2.0-flash (default)
 *
 * If the feature flag for a given capability is disabled, the agent falls back
 * to deterministic rule-based logic identical to [MockOpenClawAgent], so
 * swapping in this class is always safe even if the API key is missing.
 */
@Singleton
class RealOpenClawAgent @Inject constructor(
    private val promptBuilder: OpenClawPromptBuilder,
    private val geminiClient: GeminiApiClient,
    private val groqClient: GroqApiClient,
    private val googleServicesContextProvider: GoogleServicesContextProvider,
) : OpenClawAgent {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OpenClawAgent: Situation Analysis
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun analyzeSituation(model: SituationModel): SituationAnalysis {
        val reasoningEnabled = com.contextos.core.service.BuildConfig.OPENCLAW_ENABLE_REASONING

        if (!reasoningEnabled) {
            Log.d(TAG, "Reasoning disabled — using rule-based fallback")
            return ruleBasedAnalysis(model)
        }

        val reasoningModel = com.contextos.core.service.BuildConfig.OPENCLAW_REASONING_MODEL.takeIf { it.isNotEmpty() }
            ?: DEFAULT_REASONING_MODEL

        return try {
            val prompt = promptBuilder.buildSituationPrompt(model)
            val rawResponse = generateSingleTurn(reasoningModel, prompt, temperature = 0.3f)
            Log.d(TAG, "Reasoning response: ${rawResponse.take(200)}")
            parseSituationAnalysis(rawResponse)
        } catch (e: Exception) {
            Log.w(TAG, "LLM reasoning failed — falling back to rules", e)
            ruleBasedAnalysis(model)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OpenClawAgent: Message Drafting
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun draftMessage(context: DraftingContext): String {
        val draftingEnabled = com.contextos.core.service.BuildConfig.OPENCLAW_ENABLE_DRAFTING

        if (!draftingEnabled) {
            Log.d(TAG, "Drafting disabled — using rule-based fallback")
            return ruleBasedDraft(context)
        }

        val draftingModel = com.contextos.core.service.BuildConfig.OPENCLAW_DRAFTING_MODEL.takeIf { it.isNotEmpty() }
            ?: DEFAULT_DRAFTING_MODEL

        return try {
            val prompt = promptBuilder.buildDraftingPrompt(context)
            val rawResponse = generateSingleTurn(draftingModel, prompt, temperature = 0.7f)
            val draft = rawResponse
                .trim()
                .removeSurrounding("\"")  // LLM sometimes wraps in quotes
                .trim()

            if (draft.isBlank()) {
                Log.w(TAG, "LLM returned empty draft — using fallback")
                ruleBasedDraft(context)
            } else {
                Log.d(TAG, "LLM draft: $draft")
                draft
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM drafting failed — falling back to rules", e)
            ruleBasedDraft(context)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON Response Parsing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses the LLM's JSON response into a [SituationAnalysis].
     *
     * The LLM is instructed (via [OpenClawPromptBuilder]) to return JSON matching
     * the SituationAnalysis schema. We handle common LLM quirks:
     *   - Markdown code fences (```json ... ```)
     *   - Extra whitespace or trailing text
     *   - Missing optional fields
     */
    private fun parseSituationAnalysis(rawResponse: String): SituationAnalysis {
        val jsonString = extractJsonBlock(rawResponse)
        val obj = json.parseToJsonElement(jsonString).jsonObject

        val contextLabel = obj["currentContextLabel"]?.jsonPrimitive?.content ?: "Unknown"
        val urgencyLevel = obj["urgencyLevel"]?.jsonPrimitive?.int ?: 0

        val skills = (obj["recommendedSkills"] as? JsonArray)?.map { element ->
            val skill = element.jsonObject
            ActionRecommendation(
                skillId = skill["skillId"]?.jsonPrimitive?.content ?: "",
                confidence = skill["confidence"]?.jsonPrimitive?.float ?: 0f,
                reasoning = skill["reasoning"]?.jsonPrimitive?.content ?: ""
            )
        } ?: emptyList()

        val anomalies = (obj["anomalyFlags"] as? JsonArray)?.map { element ->
            element.jsonPrimitive.content
        } ?: emptyList()

        return SituationAnalysis(
            currentContextLabel = contextLabel,
            urgencyLevel = urgencyLevel,
            recommendedSkills = skills,
            anomalyFlags = anomalies,
        )
    }

    /**
     * Extracts a JSON block from the LLM output, stripping markdown fences and
     * any surrounding prose.
     */
    private fun extractJsonBlock(raw: String): String {
        // Try to find ```json ... ``` or ``` ... ```
        val fencePattern = Regex("""```(?:json)?\s*\n?(.*?)\n?\s*```""", RegexOption.DOT_MATCHES_ALL)
        fencePattern.find(raw)?.let { return it.groupValues[1].trim() }

        // Otherwise, find the first { ... } block
        val braceStart = raw.indexOf('{')
        val braceEnd = raw.lastIndexOf('}')
        if (braceStart >= 0 && braceEnd > braceStart) {
            return raw.substring(braceStart, braceEnd + 1)
        }

        // Last resort — return as-is, let the parser throw a clear error
        return raw
    }

    private suspend fun generateSingleTurn(
        model: String,
        prompt: String,
        temperature: Float,
    ): String {
        return when (com.contextos.core.service.BuildConfig.OPENCLAW_API_PROVIDER) {
            "groq" -> groqClient.generateMultiTurn(
                model = model,
                messages = listOf(GroqMessage(role = "user", content = prompt)),
                temperature = temperature,
                maxTokens = 2048,
            )
            else -> geminiClient.generateMultiTurn(
                model = model,
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)), role = "user")
                ),
                temperature = temperature,
                maxOutputTokens = 2048,
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule-Based Fallbacks (identical to MockOpenClawAgent)
    // ─────────────────────────────────────────────────────────────────────────

    private fun ruleBasedAnalysis(model: SituationModel): SituationAnalysis {
        val contextLabel = determineContext(model)
        val urgency = determineUrgency(model)
        val anomalies = detectAnomalies(model)
        return SituationAnalysis(
            currentContextLabel = contextLabel,
            urgencyLevel = urgency,
            recommendedSkills = emptyList(),
            anomalyFlags = anomalies,
        )
    }

    private fun ruleBasedDraft(context: DraftingContext): String {
        return when (context.reason) {
            "Running late" -> {
                val eta = context.estimatedTimeOfArrival ?: "soon"
                "Hey ${context.recipientName}, stuck in traffic — should be there by $eta. Sorry!"
            }
            "Battery warning" -> {
                val time = context.timeAvailable ?: "later"
                val backup = context.backupNumber?.let { " — call $it if urgent" } ?: ""
                "My phone's dying, in meetings until $time$backup"
            }
            "On my way" -> {
                val etaMinutes = context.estimatedTimeOfArrival ?: "a few"
                "Leaving now, ETA is about $etaMinutes minutes"
            }
            else -> {
                "Hey ${context.recipientName}, just wanted to let you know: ${context.reason}."
            }
        }
    }

    private fun determineContext(model: SituationModel): String {
        val nextEvent = model.nextCalendarEvent
        if (nextEvent != null) {
            val timeToEvent = nextEvent.startTime - model.currentTime
            if (timeToEvent in 0..15 * 60 * 1000) {
                return "Pre-meeting preparation"
            }
        }
        if (model.locationLabel == "Home") return "Free time"
        if (model.locationLabel == "Office") return "At office"
        return "Unknown"
    }

    private fun determineUrgency(model: SituationModel): Int {
        var urgency = 0
        if (model.batteryLevel < 15 && !model.isCharging) {
            urgency = maxOf(urgency, 2)
        }
        val nextEvent = model.nextCalendarEvent
        if (nextEvent != null) {
            val timeToEvent = nextEvent.startTime - model.currentTime
            if (timeToEvent in 0..5 * 60 * 1000) {
                urgency = maxOf(urgency, 3)
            }
        }
        return urgency
    }

    private fun detectAnomalies(model: SituationModel): List<String> {
        val anomalies = mutableListOf<String>()
        val nextEvent = model.nextCalendarEvent
        if (nextEvent != null && nextEvent.location != null && !nextEvent.isVirtual) {
            val timeToEvent = nextEvent.startTime - model.currentTime
            if (timeToEvent in 0..15 * 60 * 1000 && model.locationLabel != nextEvent.location) {
                anomalies.add("meeting in ${timeToEvent / 60000} minutes but user location does not match event location")
            }
        }
        return anomalies
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OpenClawAgent: Multi-Turn Chat
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun chat(history: List<ChatTurn>): String {
        val chatModel = com.contextos.core.service.BuildConfig.OPENCLAW_CHAT_MODEL.takeIf { it.isNotEmpty() }
            ?: DEFAULT_CHAT_MODEL
        val provider = com.contextos.core.service.BuildConfig.OPENCLAW_API_PROVIDER
        val augmentedHistory = augmentHistoryWithGoogleContext(history)

        return try {
            when (provider) {
                "groq" -> {
                    // Convert ChatTurn to GroqMessage format (OpenAI-compatible)
                    val messages = augmentedHistory.map { turn ->
                        GroqMessage(
                            role = when (turn.role) {
                                "assistant", "model" -> "assistant"
                                "system" -> "system"
                                else -> "user"
                            },
                            content = turn.content
                        )
                    }
                    val response = groqClient.generateMultiTurn(
                        model = chatModel,
                        messages = messages,
                        temperature = 0.7f,
                        maxTokens = 2048,
                    )
                    Log.d(TAG, "Groq chat response: ${response.take(200)}")
                    response
                }
                else -> {
                    // Gemini format (default)
                    val contents = mutableListOf<GeminiContent>()
                    for (turn in augmentedHistory) {
                        val geminiRole = when (turn.role) {
                            "system" -> "user"
                            "assistant", "model" -> "model"
                            else -> "user"
                        }
                        contents.add(
                            GeminiContent(
                                parts = listOf(GeminiPart(text = turn.content)),
                                role = geminiRole,
                            )
                        )
                    }
                    val merged = mergeConsecutiveTurns(contents)
                    val response = geminiClient.generateMultiTurn(
                        model = chatModel,
                        contents = merged,
                        temperature = 0.7f,
                        maxOutputTokens = 2048,
                    )
                    Log.d(TAG, "Gemini chat response: ${response.take(200)}")
                    response
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Chat generation failed", e)
            "I'm having trouble connecting to my intelligence backend right now. Please try again in a moment."
        }
    }

    private suspend fun augmentHistoryWithGoogleContext(history: List<ChatTurn>): List<ChatTurn> {
        val googleContext = googleServicesContextProvider.buildForChat(history)
        if (googleContext.isBlank()) return history

        val contextTurn = ChatTurn(
            role = "system",
            content = """
                |Google services context available to ContextOS for this response:
                |$googleContext
                |
                |Use this context only when it is relevant to the user's latest message. You have full access to manage and modify Gmail, Drive, and Calendar as requested.
            """.trimMargin(),
        )

        val firstUserIndex = history.indexOfFirst { it.role == "user" }
        return if (firstUserIndex == -1) {
            history + contextTurn
        } else {
            history.take(firstUserIndex) + contextTurn + history.drop(firstUserIndex)
        }
    }

    /**
     * Merges consecutive turns with the same role into a single turn.
     * Gemini API requires strictly alternating user/model roles.
     */
    private fun mergeConsecutiveTurns(contents: List<GeminiContent>): List<GeminiContent> {
        if (contents.isEmpty()) return contents
        val merged = mutableListOf<GeminiContent>()
        var current = contents.first()

        for (i in 1 until contents.size) {
            val next = contents[i]
            if (next.role == current.role) {
                // Merge parts
                current = current.copy(
                    parts = current.parts + next.parts,
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    companion object {
        private const val TAG = "RealOpenClawAgent"
        private const val DEFAULT_REASONING_MODEL = "gemini-2.0-flash"
        private const val DEFAULT_DRAFTING_MODEL = "gemini-2.0-flash-lite"
        private const val DEFAULT_CHAT_MODEL = "gemini-2.0-flash"
    }
}
