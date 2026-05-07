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
 * Production implementation of [OpenClawAgent] backed by the Gemini REST API.
 *
 * Uses [OpenClawPromptBuilder] to construct structured prompts and
 * [GeminiApiClient] to send them to the configured Gemini models.
 *
 * Model selection (from `env.sh`):
 *   - Situation analysis → `OPENCLAW_REASONING_MODEL` (default: gemini-2.0-flash)
 *   - Message drafting   → `OPENCLAW_DRAFTING_MODEL`  (default: gemini-2.0-flash-lite)
 *
 * If the feature flag for a given capability is disabled, the agent falls back
 * to deterministic rule-based logic identical to [MockOpenClawAgent], so
 * swapping in this class is always safe even if the API key is missing.
 */
@Singleton
class RealOpenClawAgent @Inject constructor(
    private val promptBuilder: OpenClawPromptBuilder,
    private val geminiClient: GeminiApiClient,
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
            val rawResponse = geminiClient.generate(reasoningModel, prompt)
            Log.d(TAG, "Gemini reasoning response: ${rawResponse.take(200)}")
            parseSituationAnalysis(rawResponse)
        } catch (e: Exception) {
            Log.w(TAG, "Gemini reasoning failed — falling back to rules", e)
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
            val rawResponse = geminiClient.generate(draftingModel, prompt)
            val draft = rawResponse
                .trim()
                .removeSurrounding("\"")  // LLM sometimes wraps in quotes
                .trim()

            if (draft.isBlank()) {
                Log.w(TAG, "Gemini returned empty draft — using fallback")
                ruleBasedDraft(context)
            } else {
                Log.d(TAG, "Gemini draft: $draft")
                draft
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini drafting failed — falling back to rules", e)
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

        return try {
            // Build the Gemini contents array from chat history.
            // The first system-role turn becomes the first "user" turn with the system prompt
            // (Gemini API uses "user"/"model" roles, not "system").
            val contents = mutableListOf<GeminiContent>()

            for (turn in history) {
                val geminiRole = when (turn.role) {
                    "system" -> "user"    // Gemini treats system as user context
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

            // Gemini requires alternating user/model turns. If we have consecutive same-role turns
            // (e.g. system + user), merge them.
            val merged = mergeConsecutiveTurns(contents)

            val response = geminiClient.generateMultiTurn(
                model = chatModel,
                contents = merged,
                temperature = 0.7f,
                maxOutputTokens = 2048,
            )
            Log.d(TAG, "Chat response: ${response.take(200)}")
            response
        } catch (e: Exception) {
            Log.w(TAG, "Chat generation failed", e)
            "I'm having trouble connecting to my intelligence backend right now. Please try again in a moment."
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
