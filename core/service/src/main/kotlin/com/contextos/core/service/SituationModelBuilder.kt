package com.contextos.core.service

import android.util.Log
import com.contextos.core.data.db.dao.CalendarEventCacheDao
import com.contextos.core.data.db.entity.CalendarEventCacheEntity
import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.data.model.RawSensorData
import com.contextos.core.data.model.SituationModel
import com.contextos.core.memory.LocationMemoryManager
import com.contextos.core.service.agent.MemorySummaryBuilder
import com.contextos.core.service.agent.SituationModeler
import org.json.JSONArray
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a [SituationModel] from a [RawSensorData] snapshot and the Room DB cache.
 *
 * Responsibilities:
 * - Map raw sensor fields 1-to-1 onto the model
 * - Resolve the next calendar event and up to 5 upcoming events from the cache
 * - Infer `locationLabel` from [LocationMemoryManager]
 * - Populate `memorySummary` from [MemorySummaryBuilder] (aggregates all memory managers)
 * - Perform `SituationAnalysis` using [SituationModeler]
 */
@Singleton
class SituationModelBuilder @Inject constructor(
    private val calendarDao: CalendarEventCacheDao,
    private val locationMemoryManager: LocationMemoryManager,
    private val memorySummaryBuilder: MemorySummaryBuilder,
    private val situationModeler: SituationModeler,
) {

    suspend fun build(raw: RawSensorData): SituationModel {
        val upcomingEntities = try {
            calendarDao.getUpcomingEvents(afterMs = raw.timestampMs)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load calendar cache", e)
            emptyList()
        }

        val nextEvent     = upcomingEntities.firstOrNull()?.toSummary()
        val upcomingEvents = upcomingEntities.take(5).map { it.toSummary() }

        val locationLabel = if (raw.location != null) {
            locationMemoryManager.getLabelForLocation(raw.location.latitude, raw.location.longitude)
        } else "Unknown"

        val calendar = Calendar.getInstance().apply { timeInMillis = raw.timestampMs }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        val roundedMinutes = if (minutes < 30) 0 else 30
        val timeSlot = String.format("%02d:%02d", hour, roundedMinutes)

        val memorySummary = memorySummaryBuilder.build(
            dayOfWeek = dayOfWeek,
            timeSlot  = timeSlot,
            latitude  = raw.location?.latitude,
            longitude = raw.location?.longitude,
        )

        val baseModel = SituationModel(
            currentTime          = raw.timestampMs,
            currentLocation      = raw.location,
            batteryLevel         = raw.batteryLevel,
            isCharging           = raw.isCharging,
            nextCalendarEvent    = nextEvent,
            upcomingCalendarEvents = upcomingEvents,
            recentAppUsage       = raw.foregroundApps,
            ambientAudioContext  = raw.ambientAudioContext,
            memorySummary        = memorySummary,
            recommendedActions   = emptyList(), // This will be overwritten by analysis.recommendedSkills
            locationLabel        = locationLabel,
            wifiSsid             = raw.wifiSsid,
            isMobileDataConnected = raw.isMobileDataConnected,
        )

        val analysis = situationModeler.analyze(baseModel)

        return baseModel.copy(
            recommendedActions = analysis.recommendedSkills,
            memorySummary = if (memorySummary.isNotEmpty()) memorySummary else analysis.currentContextLabel,
            locationLabel = if (locationLabel != "Unknown") locationLabel else analysis.currentContextLabel,
            analysis = analysis
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun CalendarEventCacheEntity.toSummary(): CalendarEventSummary {
        val attendeeList = try {
            val arr = JSONArray(attendeesJson)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
        return CalendarEventSummary(
            eventId     = eventId,
            title       = title,
            location    = location,
            startTime   = startTime,
            endTime     = endTime,
            attendees   = attendeeList,
            meetingLink = meetingLink,
            isVirtual   = isVirtual,
        )
    }

    companion object {
        private const val TAG = "SituationModelBuilder"
    }
}
