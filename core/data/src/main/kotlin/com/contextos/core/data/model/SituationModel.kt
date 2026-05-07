package com.contextos.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SituationModel(
    val currentTime: Long,                          // epoch millis
    val currentLocation: LatLng?,
    val batteryLevel: Int,                          // 0-100
    val isCharging: Boolean,
    val nextCalendarEvent: CalendarEventSummary?,
    val upcomingCalendarEvents: List<CalendarEventSummary> = emptyList(),
    val recentAppUsage: List<AppUsageEntry> = emptyList(),
    val ambientAudioContext: AmbientAudioContext = AmbientAudioContext.UNKNOWN,
    val memorySummary: String = "",
    val recommendedActions: List<ActionRecommendation> = emptyList(),
    val locationLabel: String = "Unknown",
    val wifiSsid: String? = null,
    val isMobileDataConnected: Boolean = false,
    val analysis: SituationAnalysis? = null,

    // Phase 11.1 — Galaxy Watch wearable context
    val wearableSummary: String? = null,

    // Phase 11.2 — Galaxy Buds audio context
    val budsReasoningPoints: List<String>? = null,
    val budsAnomalyFlags: List<String>? = null,
    val budsSuppressDnd: Boolean = false,

    // Phase 11.3 — SmartThings presence context
    val smartThingsIsHome: Boolean? = null,
    val smartThingsMode: String? = null,
)

@Serializable
data class LatLng(val latitude: Double, val longitude: Double)

@Serializable
data class CalendarEventSummary(
    val eventId: String,
    val title: String,
    val location: String?,
    val startTime: Long,   // epoch millis
    val endTime: Long,
    val attendees: List<String> = emptyList(),
    val meetingLink: String? = null,
    val isVirtual: Boolean = false,
)

@Serializable
data class AppUsageEntry(val packageName: String, val appName: String, val usageTimeMs: Long)

@Serializable
enum class AmbientAudioContext { SILENT, CONVERSATION, MUSIC, AMBIENT_NOISE, UNKNOWN }

@Serializable
data class ActionRecommendation(
    val skillId: String,
    val confidence: Float,
    val reasoning: String,
)
