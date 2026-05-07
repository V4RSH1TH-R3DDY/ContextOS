package com.contextos.core.service

import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.data.model.ReasoningPayload
import com.contextos.core.data.model.SituationModel

/**
 * Converts raw [SituationModel] fields into human-readable reasoning bullet strings.
 *
 * This is pure template logic — no LLM needed. Constructs reasoning strings from:
 *   - Calendar event timing
 *   - GPS / location context
 *   - Battery state
 *   - Audio context
 *   - Memory summary (routine history)
 *   - Galaxy Watch wearable data (Phase 11.1)
 *   - Galaxy Buds audio context (Phase 11.2)
 *
 * Phase 9.1 — Reasoning Data Layer
 * Phase 11.1 — Galaxy Watch context integration
 * Phase 11.2 — Galaxy Buds context integration
 * Phase 12.1 — ReasoningBuilder uses pure template logic (no LLM needed)
 */
class ReasoningBuilder {

    fun buildReasoningPayload(model: SituationModel, skillId: String, confidence: Float): ReasoningPayload {
        val reasoningPoints = mutableListOf<String>()
        val dataSourcesUsed = mutableListOf<String>()
        val anomalyFlags = mutableListOf<String>()

        // Context label based on situation
        val contextLabel = determineContextLabel(model)

        // Build reasoning points from situation model
        model.nextCalendarEvent?.let { event ->
            val minutesUntil = ((event.startTime - model.currentTime) / 60000).toInt()
            if (minutesUntil > 0) {
                reasoningPoints.add("Meeting in $minutesUntil mins: ${event.title}")
            }
            dataSourcesUsed.add("Calendar")

            // Anomaly: close to meeting but far from location
            if (minutesUntil in 1..15 && event.location != null && !event.isVirtual) {
                if (model.locationLabel != event.location && model.locationLabel != "Unknown") {
                    anomalyFlags.add("Meeting in $minutesUntil mins but you appear to be at ${model.locationLabel}")
                }
            }
        }

        // Location-based reasoning
        model.currentLocation?.let { location ->
            reasoningPoints.add("Current location: ${model.locationLabel}")
            dataSourcesUsed.add("GPS")
        }

        // Battery reasoning
        if (model.batteryLevel < 20) {
            reasoningPoints.add("Battery low: ${model.batteryLevel}%")
            dataSourcesUsed.add("Battery")
            if (model.batteryLevel < 10 && !model.isCharging) {
                anomalyFlags.add("Critical battery level — ${model.batteryLevel}% remaining")
            }
        }

        // Audio context
        if (model.ambientAudioContext != com.contextos.core.data.model.AmbientAudioContext.UNKNOWN) {
            reasoningPoints.add("Audio context: ${model.ambientAudioContext.name.lowercase()}")
            dataSourcesUsed.add("Microphone")
        }

        // Memory summary
        if (model.memorySummary.isNotEmpty()) {
            reasoningPoints.add(model.memorySummary)
            dataSourcesUsed.add("Your history")
        }

        // Wearable context (Phase 11.1 — Galaxy Watch)
        model.wearableSummary?.let { summary ->
            if (summary.isNotEmpty()) {
                reasoningPoints.add(summary)
                dataSourcesUsed.add("Galaxy Watch")
            }
        }

        // Buds context (Phase 11.2 — Galaxy Buds)
        model.budsReasoningPoints?.forEach { point ->
            reasoningPoints.add(point)
        }
        if (model.budsReasoningPoints?.isNotEmpty() == true) {
            dataSourcesUsed.add("Galaxy Buds")
        }
        model.budsAnomalyFlags?.forEach { flag ->
            anomalyFlags.add(flag)
        }

        // Connectivity context
        model.wifiSsid?.let { ssid ->
            reasoningPoints.add("Connected to $ssid")
            dataSourcesUsed.add("WiFi")
        }

        // Build dismissed skill reasoning if this is a skipped skill
        if (skillId.isNotEmpty() && confidence < 0.5f) {
            reasoningPoints.add("Skill '$skillId' evaluated but did not meet confidence threshold")
        }

        return ReasoningPayload(
            contextLabel = contextLabel,
            confidenceScore = confidence,
            reasoningPoints = reasoningPoints,
            anomalyFlags = anomalyFlags,
            dataSourcesUsed = dataSourcesUsed.distinct()
        )
    }

    /**
     * Builds a reasoning payload specifically for dismissed skills.
     *
     * Phase 9.1 — Shows "why this skill did NOT trigger" entries
     * for dismissed candidates (shown in collapsed state only in UI).
     */
    fun buildDismissedSkillReasoning(
        model: SituationModel,
        skillId: String,
        skillName: String,
        dismissalReason: String,
    ): ReasoningPayload {
        return ReasoningPayload(
            contextLabel = determineContextLabel(model),
            confidenceScore = 0.0f,
            reasoningPoints = listOf(
                "Skill '$skillName' did not trigger",
                dismissalReason,
            ),
            anomalyFlags = emptyList(),
            dataSourcesUsed = emptyList(),
        )
    }

    private fun determineContextLabel(model: SituationModel): String {
        val nextEvent = model.nextCalendarEvent ?: return "Idle"

        val minutesUntil = ((nextEvent.startTime - model.currentTime) / 60000).toInt()

        return when {
            minutesUntil in 0..5 -> "Imminent meeting"
            minutesUntil in 5..30 -> "Pre-meeting preparation"
            minutesUntil in 30..120 -> "Pre-meeting commute"
            minutesUntil > 120 -> "Upcoming event"
            minutesUntil < 0 -> "In meeting"
            else -> "In meeting"
        }
    }
}
