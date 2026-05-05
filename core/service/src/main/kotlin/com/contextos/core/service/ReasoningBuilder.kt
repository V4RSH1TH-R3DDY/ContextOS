package com.contextos.core.service

import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.data.model.ReasoningPayload
import com.contextos.core.data.model.SituationModel

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

        return ReasoningPayload(
            contextLabel = contextLabel,
            confidenceScore = confidence,
            reasoningPoints = reasoningPoints,
            anomalyFlags = anomalyFlags,
            dataSourcesUsed = dataSourcesUsed.distinct()
        )
    }

    private fun determineContextLabel(model: SituationModel): String {
        val nextEvent = model.nextCalendarEvent ?: return "Idle"

        val minutesUntil = ((nextEvent.startTime - model.currentTime) / 60000).toInt()

        return when {
            minutesUntil in 5..30 -> "Pre-meeting preparation"
            minutesUntil in 30..120 -> "Pre-meeting commute"
            minutesUntil > 120 -> "Upcoming event"
            else -> "In meeting"
        }
    }
}
