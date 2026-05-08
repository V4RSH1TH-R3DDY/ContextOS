package com.contextos.core.service.agent.openclaw

import android.util.Log
import com.contextos.core.data.model.ActionRecommendation
import com.contextos.core.data.model.DraftingContext
import com.contextos.core.data.model.SituationAnalysis
import com.contextos.core.data.model.SituationModel
import com.contextos.core.network.CalendarApiClient
import com.contextos.core.network.DriveApiClient
import com.contextos.core.network.GmailApiClient
import com.contextos.core.service.SensorDataCollector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
    private val sensorDataCollector: SensorDataCollector,
    private val driveApiClient: DriveApiClient,
    private val calendarApiClient: CalendarApiClient,
    private val gmailApiClient: GmailApiClient,
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
        val provider = com.contextos.core.service.BuildConfig.OPENCLAW_API_PROVIDER

        return try {
            val prompt = promptBuilder.buildSituationPrompt(model)
            if (provider == "groq") {
                analyzeWithTools(reasoningModel, prompt)
            } else {
                val rawResponse = generateSingleTurn(reasoningModel, prompt, temperature = 0.3f)
                Log.d(TAG, "Reasoning response: ${rawResponse.take(200)}")
                parseSituationAnalysis(rawResponse)
            }
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

        // Inject live device context + Google services context before the first user turn
        val deviceContext = buildChatDeviceContext()
        val deviceAugmented = if (deviceContext.isNotBlank()) {
            injectContextTurn(history, deviceContext)
        } else {
            history
        }
        val augmentedHistory = augmentHistoryWithGoogleContext(deviceAugmented)

        return try {
            when (provider) {
                "groq" -> chatWithTools(chatModel, augmentedHistory)
                else -> geminiChat(chatModel, augmentedHistory)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Chat generation failed", e)
            "I'm having trouble connecting to my intelligence backend right now. Please try again in a moment."
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Groq chat with tool-calling
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun chatWithTools(
        model: String,
        history: List<ChatTurn>,
    ): String {
        var messages = history.map { turn ->
            GroqMessage(
                role = when (turn.role) {
                    "assistant", "model" -> "assistant"
                    "system" -> "system"
                    else -> "user"
                },
                content = turn.content,
            )
        }

        var finalText = ""
        for (round in 0 until MAX_TOOL_ROUNDS) {
            val responseMsg = groqClient.generateWithTools(
                model = model,
                messages = messages,
                tools = ALL_TOOLS,
                temperature = 0.7f,
                maxTokens = 2048,
            )

            if (responseMsg.toolCalls.isNullOrEmpty()) {
                finalText = responseMsg.content?.trim() ?: ""
                break
            }

            messages = messages + responseMsg

            for (toolCall in responseMsg.toolCalls) {
                val result = executeToolCall(toolCall)
                messages = messages + GroqMessage(
                    role = "tool",
                    toolCallId = toolCall.id,
                    content = result,
                )
            }
        }

        // If all rounds were consumed by tool calls, force a final summary
        if (finalText.isBlank()) {
            Log.d(TAG, "Tool rounds exhausted — requesting final summary")
            finalText = try {
                groqClient.generateMultiTurn(
                    model = model,
                    messages = messages + GroqMessage(
                        role = "user",
                        content = "Based on the tool results above, provide a brief summary of what was done.",
                    ),
                    temperature = 0.7f,
                    maxTokens = 1024,
                ).trim()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get final summary", e)
                "I processed your request using the available tools. Check the action log for details."
            }
        }

        Log.d(TAG, "Groq chat response: ${finalText.take(200)}")
        return finalText
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gemini chat (no tool support yet)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun geminiChat(
        model: String,
        history: List<ChatTurn>,
    ): String {
        val contents = mutableListOf<GeminiContent>()
        for (turn in history) {
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
            model = model,
            contents = merged,
            temperature = 0.7f,
            maxOutputTokens = 2048,
        )
        Log.d(TAG, "Gemini chat response: ${response.take(200)}")
        return response
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool definitions & execution
    // ─────────────────────────────────────────────────────────────────────────

    private val DRIVE_TOOLS = listOf(
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "drive_create_folder",
                description = "Create a new folder in Google Drive",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("name", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Display name for the folder"))
                        })
                        put("parentFolderId", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Parent folder ID. Omit to create in root Drive."))
                        })
                    })
                    put("required", buildJsonArray { add(JsonPrimitive("name")) })
                },
            )
        ),
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "drive_move_file",
                description = "Move a file or folder into a different parent folder in Google Drive",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("fileId", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The ID of the file or folder to move"))
                        })
                        put("newParentFolderId", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The target folder ID to move into"))
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("fileId"))
                        add(JsonPrimitive("newParentFolderId"))
                    })
                },
            )
        ),
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "drive_search_files",
                description = "Search for files and folders in Google Drive using the Drive query language",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("query", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Drive query, e.g. \"name contains 'notes'\" or \"mimeType = 'application/pdf'\""))
                        })
                    })
                    put("required", buildJsonArray { add(JsonPrimitive("query")) })
                },
            )
        ),
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "drive_list_recent_files",
                description = "List the most recently modified files in Google Drive",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("maxResults", buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put("description", JsonPrimitive("Number of files to return (default 10)"))
                        })
                    })
                    put("required", buildJsonArray { })
                },
            )
        ),
    )

    private val CALENDAR_TOOLS = listOf(
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "calendar_list_events",
                description = "List upcoming events on the primary Google Calendar",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("daysAhead", buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put("description", JsonPrimitive("How many days ahead to look (default 7)"))
                        })
                    })
                    put("required", buildJsonArray { })
                },
            )
        ),
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "calendar_create_event",
                description = "Create a new event on the primary Google Calendar",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("summary", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Event title"))
                        })
                        put("startTimeIso", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Start time in ISO 8601, e.g. \"2026-05-08T19:30:00\""))
                        })
                        put("endTimeIso", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("End time in ISO 8601, e.g. \"2026-05-08T20:30:00\""))
                        })
                        put("description", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Optional event description"))
                        })
                        put("location", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Optional physical location"))
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("summary"))
                        add(JsonPrimitive("startTimeIso"))
                        add(JsonPrimitive("endTimeIso"))
                    })
                },
            )
        ),
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "calendar_update_event",
                description = "Update an existing event on the primary Google Calendar. Only provided fields are changed.",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("eventId", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The ID of the event to update"))
                        })
                        put("summary", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("New event title"))
                        })
                        put("startTimeIso", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("New start time in ISO 8601"))
                        })
                        put("endTimeIso", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("New end time in ISO 8601"))
                        })
                        put("description", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("New description"))
                        })
                        put("location", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("New location"))
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("eventId"))
                    })
                },
            )
        ),
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "calendar_delete_event",
                description = "Delete an event from the primary Google Calendar",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("eventId", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The ID of the event to delete"))
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("eventId"))
                    })
                },
            )
        ),
    )

    private val GMAIL_TOOLS = listOf(
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "gmail_search",
                description = "Search the user's Gmail inbox for messages matching a query",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("query", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Search query, e.g. \"from:someone@example.com\" or \"subject:meeting\""))
                        })
                        put("maxResults", buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put("description", JsonPrimitive("Maximum number of results (default 5)"))
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("query"))
                    })
                },
            )
        ),
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "gmail_get_message",
                description = "Get the full details of a specific email message by ID",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("messageId", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The ID of the message to fetch"))
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("messageId"))
                    })
                },
            )
        ),
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "gmail_send",
                description = "Send an email from the user's Gmail account",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("to", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Recipient email address"))
                        })
                        put("subject", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Email subject"))
                        })
                        put("body", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Plain text email body"))
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("to"))
                        add(JsonPrimitive("subject"))
                        add(JsonPrimitive("body"))
                    })
                },
            )
        ),
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "gmail_trash",
                description = "Move an email to the Trash folder",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("messageId", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The ID of the message to trash"))
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("messageId"))
                    })
                },
            )
        ),
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "gmail_delete",
                description = "Permanently delete an email",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("messageId", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The ID of the message to permanently delete"))
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("messageId"))
                    })
                },
            )
        ),
        GroqToolDefinition(
            type = "function",
            function = GroqFunctionDefinition(
                name = "gmail_modify",
                description = "Modify labels on an email (mark as read, add star, move to folder, etc.)",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("messageId", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The ID of the message to modify"))
                        })
                        put("addLabelIds", buildJsonObject {
                            put("type", JsonPrimitive("array"))
                            put("description", JsonPrimitive("Labels to add, e.g. [\"STARRED\", \"IMPORTANT\"]. Use \"TRASH\" to move to trash."))
                            put("items", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            })
                        })
                        put("removeLabelIds", buildJsonObject {
                            put("type", JsonPrimitive("array"))
                            put("description", JsonPrimitive("Labels to remove, e.g. [\"UNREAD\"] to mark as read."))
                            put("items", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            })
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("messageId"))
                    })
                },
            )
        ),
    )

    private val ALL_TOOLS = DRIVE_TOOLS + CALENDAR_TOOLS + GMAIL_TOOLS

    /** Read-only subset: the LLM can use these during autonomous analysis to gather context. */
    private val READ_ONLY_TOOLS = ALL_TOOLS.filter { tool ->
        tool.function.name.startsWith("calendar_list_") ||
        tool.function.name.startsWith("gmail_search") ||
        tool.function.name.startsWith("gmail_get_") ||
        tool.function.name.startsWith("drive_search_") ||
        tool.function.name.startsWith("drive_list_")
    }

    /**
     * Tool-calling analysis loop for the autonomous agent cycle.
     *
     * The LLM may use read-only tools (calendar, email, Drive search) to gather
     * additional context beyond what's in the prompt. When it has enough context
     * it returns a text response which is parsed as [SituationAnalysis] JSON.
     *
     * Falls back to [parseSituationAnalysis] on the final text, or throws so the
     * caller can fall back to rule-based analysis.
     */
    private suspend fun analyzeWithTools(
        modelName: String,
        prompt: String,
    ): SituationAnalysis {
        val messages = mutableListOf(GroqMessage(role = "user", content = prompt))

        for (round in 0 until MAX_TOOL_ROUNDS) {
            val responseMsg = groqClient.generateWithTools(
                model = modelName,
                messages = messages,
                tools = READ_ONLY_TOOLS,
                temperature = 0.3f,
                maxTokens = 2048,
            )

            if (responseMsg.toolCalls.isNullOrEmpty()) {
                val text = responseMsg.content?.trim() ?: ""
                Log.d(TAG, "Analysis response: ${text.take(200)}")
                return parseSituationAnalysis(text)
            }

            messages.add(responseMsg)
            for (toolCall in responseMsg.toolCalls) {
                val result = executeToolCall(toolCall)
                messages.add(GroqMessage(role = "tool", toolCallId = toolCall.id, content = result))
            }
        }

        throw IllegalStateException("Analysis did not produce a result after $MAX_TOOL_ROUNDS rounds")
    }

    private suspend fun executeToolCall(toolCall: GroqToolCall): String {
        return try {
            val args = json.parseToJsonElement(toolCall.function.arguments).jsonObject
            when (toolCall.function.name) {
                "drive_create_folder" -> {
                    val name = args["name"]?.jsonPrimitive?.content ?: return "Error: missing 'name' argument"
                    val parentId = args["parentFolderId"]?.jsonPrimitive?.content
                    val folder = driveApiClient.createFolder(name, parentId)
                    if (folder != null) {
                        "Created folder \"${folder.name}\" (id: ${folder.id})${folder.webViewLink?.let { " — $it" } ?: ""}"
                    } else {
                        "Error: failed to create folder (auth or API error)"
                    }
                }
                "drive_move_file" -> {
                    val fileId = args["fileId"]?.jsonPrimitive?.content ?: return "Error: missing 'fileId' argument"
                    val newParent = args["newParentFolderId"]?.jsonPrimitive?.content ?: return "Error: missing 'newParentFolderId' argument"
                    val moved = driveApiClient.moveFile(fileId, newParent)
                    if (moved != null) {
                        "Moved \"${moved.name}\" to folder id: $newParent"
                    } else {
                        "Error: failed to move file (auth or API error)"
                    }
                }
                "drive_search_files" -> {
                    val query = args["query"]?.jsonPrimitive?.content ?: return "Error: missing 'query' argument"
                    val files = driveApiClient.searchFiles(query)
                    if (files.isEmpty()) {
                        "No files found matching \"$query\""
                    } else {
                        files.joinToString("\n") { f ->
                            "- ${f.name} (${f.mimeType ?: "unknown type"}) — id: ${f.id}${f.webViewLink?.let { " | $it" } ?: ""}"
                        }
                    }
                }
                "drive_list_recent_files" -> {
                    val maxResults = args["maxResults"]?.jsonPrimitive?.int ?: 10
                    val files = driveApiClient.listRecentFiles(maxResults)
                    if (files.isEmpty()) {
                        "No recent files found in Drive"
                    } else {
                        files.joinToString("\n") { f ->
                            "- ${f.name} (${f.mimeType ?: "unknown type"}) — modified: ${f.modifiedTime}${f.webViewLink?.let { " | $it" } ?: ""}"
                        }
                    }
                }
                "calendar_list_events" -> {
                    val daysAhead = args["daysAhead"]?.jsonPrimitive?.int ?: 7
                    val events = calendarApiClient.getUpcomingEvents(hoursAhead = daysAhead * 24)
                    if (events.isEmpty()) {
                        "No upcoming events found in the next $daysAhead days"
                    } else {
                        events.joinToString("\n") { e ->
                            val start = e.startTime?.let { java.text.SimpleDateFormat("EEE MMM d, h:mm a", java.util.Locale.US).format(java.util.Date(it)) } ?: "unknown"
                            "- ${e.title} at $start${e.location?.let { " | $it" } ?: ""}"
                        }
                    }
                }
                "calendar_create_event" -> {
                    val summary = args["summary"]?.jsonPrimitive?.content ?: return "Error: missing 'summary' argument"
                    val startTimeIso = args["startTimeIso"]?.jsonPrimitive?.content ?: return "Error: missing 'startTimeIso' argument"
                    val endTimeIso = args["endTimeIso"]?.jsonPrimitive?.content ?: return "Error: missing 'endTimeIso' argument"
                    val description = args["description"]?.jsonPrimitive?.content
                    val location = args["location"]?.jsonPrimitive?.content
                    val event = calendarApiClient.createEvent(summary, startTimeIso, endTimeIso, description, location)
                    if (event != null) {
                        "Created event \"${event.summary}\" (id: ${event.id}) — ${event.htmlLink?.let { "$it" } ?: ""}"
                    } else {
                        "Error: failed to create calendar event (auth or API error)"
                    }
                }
                "calendar_update_event" -> {
                    val eventId = args["eventId"]?.jsonPrimitive?.content ?: return "Error: missing 'eventId' argument"
                    val summary = args["summary"]?.jsonPrimitive?.content
                    val startTimeIso = args["startTimeIso"]?.jsonPrimitive?.content
                    val endTimeIso = args["endTimeIso"]?.jsonPrimitive?.content
                    val description = args["description"]?.jsonPrimitive?.content
                    val location = args["location"]?.jsonPrimitive?.content
                    val event = calendarApiClient.updateEvent(eventId, summary, startTimeIso, endTimeIso, description, location)
                    if (event != null) {
                        "Updated event \"${event.summary}\" (id: ${event.id})"
                    } else {
                        "Error: failed to update calendar event (auth or API error)"
                    }
                }
                "calendar_delete_event" -> {
                    val eventId = args["eventId"]?.jsonPrimitive?.content ?: return "Error: missing 'eventId' argument"
                    val deleted = calendarApiClient.deleteEvent(eventId)
                    if (deleted) {
                        "Deleted event $eventId"
                    } else {
                        "Error: failed to delete calendar event (auth or API error)"
                    }
                }
                "gmail_search" -> {
                    val query = args["query"]?.jsonPrimitive?.content ?: return "Error: missing 'query' argument"
                    val maxResults = args["maxResults"]?.jsonPrimitive?.int ?: 5
                    val messages = gmailApiClient.searchMessages(query, maxResults.toLong())
                    if (messages.isEmpty()) {
                        "No messages found matching \"$query\""
                    } else {
                        messages.joinToString("\n") { msg ->
                            val headers = msg.payload?.headers.orEmpty().associateBy { it.name.lowercase(java.util.Locale.US) }
                            val subject = headers["subject"]?.value?.takeIf { it.isNotBlank() } ?: "(no subject)"
                            val from = headers["from"]?.value?.takeIf { it.isNotBlank() } ?: "unknown"
                            val snippet = msg.snippet?.takeIf { it.isNotBlank() }?.let { " | ${it.take(100)}" }.orEmpty()
                            "- $subject | from: $from | id: ${msg.id}$snippet"
                        }
                    }
                }
                "gmail_get_message" -> {
                    val messageId = args["messageId"]?.jsonPrimitive?.content ?: return "Error: missing 'messageId' argument"
                    val msg = gmailApiClient.getMessage(messageId)
                    if (msg != null) {
                        val headers = msg.payload?.headers.orEmpty().associateBy { it.name.lowercase(java.util.Locale.US) }
                        val subject = headers["subject"]?.value?.takeIf { it.isNotBlank() } ?: "(no subject)"
                        val from = headers["from"]?.value?.takeIf { it.isNotBlank() } ?: "unknown"
                        val to = headers["to"]?.value?.takeIf { it.isNotBlank() } ?: "unknown"
                        val date = headers["date"]?.value?.takeIf { it.isNotBlank() } ?: "unknown"
                        val snippet = msg.snippet?.takeIf { it.isNotBlank() } ?: "(no preview)"
                        buildString {
                            appendLine("Subject: $subject")
                            appendLine("From: $from")
                            appendLine("To: $to")
                            appendLine("Date: $date")
                            appendLine("---")
                            append(snippet)
                        }
                    } else {
                        "Error: could not fetch message $messageId"
                    }
                }
                "gmail_send" -> {
                    val to = args["to"]?.jsonPrimitive?.content ?: return "Error: missing 'to' argument"
                    val subject = args["subject"]?.jsonPrimitive?.content ?: return "Error: missing 'subject' argument"
                    val body = args["body"]?.jsonPrimitive?.content ?: return "Error: missing 'body' argument"
                    val sent = gmailApiClient.sendMessage(to, subject, body)
                    if (sent != null) {
                        "Sent email to $to (id: ${sent.id})"
                    } else {
                        "Error: failed to send email (auth or API error)"
                    }
                }
                "gmail_trash" -> {
                    val messageId = args["messageId"]?.jsonPrimitive?.content ?: return "Error: missing 'messageId' argument"
                    val trashed = gmailApiClient.trashMessage(messageId)
                    if (trashed) "Moved message $messageId to Trash"
                    else "Error: failed to trash message (auth or API error)"
                }
                "gmail_delete" -> {
                    val messageId = args["messageId"]?.jsonPrimitive?.content ?: return "Error: missing 'messageId' argument"
                    val deleted = gmailApiClient.deleteMessage(messageId)
                    if (deleted) "Permanently deleted message $messageId"
                    else "Error: failed to delete message (auth or API error)"
                }
                "gmail_modify" -> {
                    val messageId = args["messageId"]?.jsonPrimitive?.content ?: return "Error: missing 'messageId' argument"
                    val addIds = args["addLabelIds"]?.jsonArray?.map { it.jsonPrimitive.content }
                    val removeIds = args["removeLabelIds"]?.jsonArray?.map { it.jsonPrimitive.content }
                    val modified = gmailApiClient.modifyMessage(messageId, addIds, removeIds)
                    if (modified) {
                        val parts = mutableListOf<String>()
                        addIds?.let { if (it.isNotEmpty()) parts.add("added labels: ${it.joinToString(", ")}") }
                        removeIds?.let { if (it.isNotEmpty()) parts.add("removed labels: ${it.joinToString(", ")}") }
                        "Modified message $messageId (${parts.joinToString(", ")})"
                    } else {
                        "Error: failed to modify message (auth or API error)"
                    }
                }
                else -> "Error: unknown tool \"${toolCall.function.name}\""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tool execution failed: ${toolCall.function.name}", e)
            "Error executing ${toolCall.function.name}: ${e.message}"
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

    private suspend fun buildChatDeviceContext(): String {
        return try {
            val raw = sensorDataCollector.collect()
            val timeStr = java.text.SimpleDateFormat("EEE MMM d, h:mm a", java.util.Locale.US)
                .format(java.util.Date(raw.timestampMs))
            buildString {
                appendLine("Current device state (live sensor snapshot):")
                appendLine("- Time: $timeStr")
                raw.location?.let {
                    appendLine("- Location: ${it.latitude}, ${it.longitude}")
                }
                appendLine("- Battery: ${raw.batteryLevel}% (charging: ${raw.isCharging})")
                appendLine("- Wi-Fi: ${raw.wifiSsid ?: "disconnected"}")
                appendLine("- Mobile data: ${raw.isMobileDataConnected}")
                appendLine("- Audio: ${raw.ambientAudioContext.name}")
                if (raw.foregroundApps.isNotEmpty()) {
                    val apps = raw.foregroundApps.take(3).joinToString { it.appName }
                    appendLine("- Recent apps: $apps")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collect live device context for chat", e)
            ""
        }
    }

    private fun injectContextTurn(
        history: List<ChatTurn>,
        contextContent: String,
    ): List<ChatTurn> {
        val contextTurn = ChatTurn(role = "system", content = contextContent)
        val firstUserIndex = history.indexOfFirst { it.role == "user" }
        return if (firstUserIndex == -1) {
            history + contextTurn
        } else {
            history.take(firstUserIndex) + contextTurn + history.drop(firstUserIndex)
        }
    }

    companion object {
        private const val TAG = "RealOpenClawAgent"
        private const val DEFAULT_REASONING_MODEL = "gemini-2.0-flash"
        private const val DEFAULT_DRAFTING_MODEL = "gemini-2.0-flash-lite"
        private const val DEFAULT_CHAT_MODEL = "gemini-2.0-flash"
        private const val MAX_TOOL_ROUNDS = 5
    }
}
