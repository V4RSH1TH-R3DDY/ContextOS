package com.contextos.core.service

import android.util.Log
import com.contextos.core.data.db.dao.CalendarEventCacheDao
import com.contextos.core.data.db.entity.CalendarEventCacheEntity
import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.data.model.RawSensorData
import com.contextos.core.data.model.SituationModel
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a [SituationModel] from a [RawSensorData] snapshot and the Room DB cache.
 *
 * Responsibilities:
 * - Map raw sensor fields 1-to-1 onto the model
 * - Resolve the next calendar event and up to 5 upcoming events from the cache
 * - Set `locationLabel` to "Unknown" (Phase 3.4 will infer real labels from memory)
 * - Leave `memorySummary` and `recommendedActions` empty (filled by Phase 3)
 */
@Singleton
class SituationModelBuilder @Inject constructor(
    private val calendarDao: CalendarEventCacheDao,
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

        return SituationModel(
            currentTime          = raw.timestampMs,
            currentLocation      = raw.location,
            batteryLevel         = raw.batteryLevel,
            isCharging           = raw.isCharging,
            nextCalendarEvent    = nextEvent,
            upcomingCalendarEvents = upcomingEvents,
            recentAppUsage       = raw.foregroundApps,
            ambientAudioContext  = raw.ambientAudioContext,
            memorySummary        = "",                // Phase 3 fills this
            recommendedActions   = emptyList(),       // Phase 3 fills this
            locationLabel        = "Unknown",         // Phase 3.4 infers labels
            wifiSsid             = raw.wifiSsid,
            isMobileDataConnected = raw.isMobileDataConnected,
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
