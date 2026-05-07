package com.contextos.core.service.agent.openclaw

import com.contextos.core.data.model.DraftingContext
import com.contextos.core.data.model.SituationAnalysis
import com.contextos.core.data.model.SituationModel
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockOpenClawAgent @Inject constructor() : OpenClawAgent {

    override suspend fun analyzeSituation(model: SituationModel): SituationAnalysis {
        // Simulate network delay for LLM inference
        delay(1500)
        
        val contextLabel = determineContext(model)
        val urgency = determineUrgency(model)
        val anomalies = detectAnomalies(model)
        
        return SituationAnalysis(
            currentContextLabel = contextLabel,
            urgencyLevel = urgency,
            recommendedSkills = emptyList(),
            anomalyFlags = anomalies
        )
    }

    override suspend fun draftMessage(context: DraftingContext): String {
        // Simulate network delay for LLM inference
        delay(1000)
        
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
    
    // Fallback rule-based logic to simulate what the LLM will output
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

    override suspend fun chat(history: List<ChatTurn>): String {
        delay(800)
        val lastUserMessage = history.lastOrNull { it.role == "user" }?.content ?: ""
        return when {
            lastUserMessage.contains("help", ignoreCase = true) ->
                "I can help you manage your phone proactively! I monitor your calendar, battery, location, and more to take smart actions. Try asking about your skills or preferences."
            lastUserMessage.contains("skill", ignoreCase = true) ->
                "You have several skills available: DND Setter, Battery Warner, Navigation Launcher, Message Drafter, Document Fetcher, and Location Intelligence. Each can be configured in the sidebar."
            else ->
                "I understand. As your context-aware assistant, I'm always learning your patterns to help you stay ahead. Is there anything specific you'd like me to help with?"
        }
    }
}
