package com.contextos.core.network

import android.util.Log
import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.data.preferences.PreferencesManager
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches calendar events from the Google Calendar API (v3).
 *
 * Rate limit budget (per contract): max 4 calls per hour across the app,
 * meaning [getUpcomingEvents] is called at service start and every 30 minutes
 * by [CalendarSyncWorker] (Phase 1.5). Raw data is cached in Room;
 * everything else reads from the cache.
 */
@Singleton
class CalendarApiClient @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val preferencesManager: PreferencesManager,
) {

    // ─── Service builder ─────────────────────────────────────────────────────

    private fun buildService(): Calendar? {
        val credential = authManager.getCredential(listOf(GoogleAuthManager.SCOPE_CALENDAR))
            ?: return null
        return Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
            .setApplicationName(APP_NAME)
            .build()
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns upcoming calendar events in the window [now, now + hoursAhead].
     *
     * - Retries up to 3× on transient [IOException] with exponential back-off.
     * - Returns an empty list (never throws) on auth errors so callers can
     *   degrade gracefully.
     * - Meeting links are extracted from both the description and location fields.
     */
    suspend fun getUpcomingEvents(hoursAhead: Int = 8): List<CalendarEventSummary> =
        syncUpcomingEvents(hoursAhead = hoursAhead, syncToken = null).events

    /**
     * Returns calendar events on the given [date] in the device's local time zone.
     */
    suspend fun getEventsForDay(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEventSummary> =
        withContext(Dispatchers.IO) {
            val service = buildService()
                ?: return@withContext emptyList<CalendarEventSummary>().also {
                    Log.w(TAG, "getEventsForDay: not signed in")
                }

            val startMs = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endMs = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            try {
                retryWithBackoff(tag = TAG) {
                    val result = service.events().list("primary")
                        .setSingleEvents(true)
                        .setTimeMin(DateTime(startMs))
                        .setTimeMax(DateTime(endMs))
                        .setOrderBy("startTime")
                        .setFields("items(id,summary,location,description,start,end,attendees,status)")
                        .execute()

                    (result.items ?: emptyList()).mapNotNull { event ->
                        if (event.status == "cancelled") return@mapNotNull null

                        val description = event.description.orEmpty()
                        val location = event.location.orEmpty()
                        val meetingLink = extractMeetingLink("$description $location")
                        val isVirtual = meetingLink != null
                            || VIRTUAL_KEYWORDS.any { kw ->
                                location.contains(kw, ignoreCase = true)
                            }

                        CalendarEventSummary(
                            eventId = event.id.orEmpty(),
                            title = event.summary.orEmpty(),
                            location = event.location,
                            startTime = event.start?.dateTime?.value
                                ?: event.start?.date?.value ?: startMs,
                            endTime = event.end?.dateTime?.value
                                ?: event.end?.date?.value ?: endMs,
                            attendees = event.attendees?.mapNotNull { it.email } ?: emptyList(),
                            meetingLink = meetingLink,
                            isVirtual = isVirtual,
                        )
                    }
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "User needs to re-authorize Calendar access", e)
                preferencesManager.setGoogleReauthRequired(true)
                emptyList()
            } catch (e: GoogleJsonResponseException) {
                Log.e(TAG, "Calendar API error", e)
                emptyList()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to fetch day events after retries", e)
                emptyList()
            }
        }

    /**
     * Syncs upcoming events and returns the next incremental sync token when Google provides it.
     *
     * If [syncToken] is present, the request uses Calendar's incremental sync mode and returns
     * changed/deleted items since the previous sync. A 410 response means the token expired and
     * callers should discard it and run a full sync.
     */
    suspend fun syncUpcomingEvents(
        hoursAhead: Int = 8,
        syncToken: String?,
    ): CalendarSyncResult =
        withContext(Dispatchers.IO) {
            val service = buildService()
                ?: return@withContext CalendarSyncResult.empty().also {
                    Log.w(TAG, "getUpcomingEvents: not signed in")
                }

            val now   = System.currentTimeMillis()
            val until = now + hoursAhead * 3_600_000L

            try {
                retryWithBackoff(tag = TAG) {
                    val request = service.events().list("primary")
                        .setSingleEvents(true)
                        .setMaxResults(50)

                    if (syncToken.isNullOrBlank()) {
                        request
                            .setTimeMin(DateTime(now))
                            .setTimeMax(DateTime(until))
                            .setOrderBy("startTime")
                    } else {
                        request.setSyncToken(syncToken)
                    }

                    val result = request.execute()
                    val deletedEventIds = mutableListOf<String>()
                    val events = (result.items ?: emptyList()).mapNotNull { event ->
                        if (event.status == "cancelled") {
                            event.id?.let { deletedEventIds += it }
                            return@mapNotNull null
                        }
                        val description = event.description.orEmpty()
                        val location    = event.location.orEmpty()
                        val meetingLink = extractMeetingLink("$description $location")
                        val isVirtual   = meetingLink != null
                            || VIRTUAL_KEYWORDS.any { kw ->
                                location.contains(kw, ignoreCase = true)
                            }

                        CalendarEventSummary(
                            eventId     = event.id.orEmpty(),
                            title       = event.summary.orEmpty(),
                            location    = event.location,
                            startTime   = event.start?.dateTime?.value
                                ?: event.start?.date?.value ?: now,
                            endTime     = event.end?.dateTime?.value
                                ?: event.end?.date?.value ?: now,
                            attendees   = event.attendees?.mapNotNull { it.email } ?: emptyList(),
                            meetingLink = meetingLink,
                            isVirtual   = isVirtual,
                        )
                    }
                    CalendarSyncResult(
                        events = events,
                        deletedEventIds = deletedEventIds,
                        nextSyncToken = result.nextSyncToken,
                        tokenExpired = false,
                    )
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "User needs to re-authorize Calendar access", e)
                preferencesManager.setGoogleReauthRequired(true)
                CalendarSyncResult.empty()
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode == HTTP_GONE) {
                    Log.w(TAG, "Calendar sync token expired; full sync required")
                    CalendarSyncResult(tokenExpired = true)
                } else {
                    Log.e(TAG, "Calendar API error", e)
                    CalendarSyncResult.empty()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to fetch events after retries", e)
                CalendarSyncResult.empty()
            }
        }

    // ─── Write Operations ────────────────────────────────────────────────────

    /**
     * Creates a new calendar event on the primary calendar.
     *
     * @param summary  Event title (required).
     * @param startTimeIso  Start time in ISO 8601, e.g. "2026-05-08T19:30:00".
     * @param endTimeIso  End time in ISO 8601, e.g. "2026-05-08T20:30:00".
     * @param description  Optional description.
     * @param location  Optional physical location.
     * @return The created Event (including id) or null on failure.
     */
    suspend fun createEvent(
        summary: String,
        startTimeIso: String,
        endTimeIso: String,
        description: String? = null,
        location: String? = null,
    ): Event? = withContext(Dispatchers.IO) {
        val service = buildService()
            ?: return@withContext null.also {
                Log.w(TAG, "createEvent: not signed in")
            }

        try {
            retryWithBackoff(tag = TAG) {
                val event = Event()
                    .setSummary(summary)
                    .setDescription(description)
                    .setLocation(location)
                    .setStart(EventDateTime()
                        .setDateTime(DateTime(startTimeIso))
                        .setTimeZone(TimeZone.getDefault().id))
                    .setEnd(EventDateTime()
                        .setDateTime(DateTime(endTimeIso))
                        .setTimeZone(TimeZone.getDefault().id))

                service.events().insert("primary", event)
                    .setFields("id,summary,description,location,start,end,htmlLink")
                    .execute()
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "User needs to re-authorize Calendar access", e)
            preferencesManager.setGoogleReauthRequired(true)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create calendar event after retries", e)
            null
        }
    }

    /**
     * Updates an existing calendar event. Only non-null fields are changed.
     */
    suspend fun updateEvent(
        eventId: String,
        summary: String? = null,
        startTimeIso: String? = null,
        endTimeIso: String? = null,
        description: String? = null,
        location: String? = null,
    ): Event? = withContext(Dispatchers.IO) {
        val service = buildService()
            ?: return@withContext null.also {
                Log.w(TAG, "updateEvent: not signed in")
            }

        try {
            retryWithBackoff(tag = TAG) {
                val event = Event()
                summary?.let { event.setSummary(it) }
                description?.let { event.setDescription(it) }
                location?.let { event.setLocation(it) }
                startTimeIso?.let {
                    event.setStart(EventDateTime()
                        .setDateTime(DateTime(it))
                        .setTimeZone(TimeZone.getDefault().id))
                }
                endTimeIso?.let {
                    event.setEnd(EventDateTime()
                        .setDateTime(DateTime(it))
                        .setTimeZone(TimeZone.getDefault().id))
                }

                service.events().update("primary", eventId, event)
                    .setFields("id,summary,description,location,start,end,htmlLink")
                    .execute()
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "User needs to re-authorize Calendar access", e)
            preferencesManager.setGoogleReauthRequired(true)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Failed to update calendar event after retries", e)
            null
        }
    }

    /**
     * Deletes a calendar event by ID.
     * @return true if deleted, false on failure.
     */
    suspend fun deleteEvent(eventId: String): Boolean = withContext(Dispatchers.IO) {
        val service = buildService()
            ?: return@withContext false.also {
                Log.w(TAG, "deleteEvent: not signed in")
            }

        try {
            retryWithBackoff(tag = TAG) {
                service.events().delete("primary", eventId).execute()
                true
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "User needs to re-authorize Calendar access", e)
            preferencesManager.setGoogleReauthRequired(true)
            false
        } catch (e: IOException) {
            Log.e(TAG, "Failed to delete calendar event after retries", e)
            false
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun extractMeetingLink(text: String): String? =
        MEETING_LINK_PATTERNS.firstNotNullOfOrNull { regex ->
            regex.find(text)?.value
        }

    // ─── Constants ───────────────────────────────────────────────────────────

    companion object {
        private const val TAG      = "CalendarApiClient"
        private const val APP_NAME = "ContextOS"
        private const val HTTP_GONE = 410

        private val HTTP_TRANSPORT = NetHttpTransport()
        private val JSON_FACTORY   = GsonFactory.getDefaultInstance()

        /** Regex patterns for extracting virtual meeting links from event text. */
        private val MEETING_LINK_PATTERNS = listOf(
            Regex("""https?://[^\s]*zoom\.us/j/[^\s]*"""),           // Zoom
            Regex("""https?://meet\.google\.com/[a-z\-]+"""),         // Google Meet
            Regex("""https?://[^\s]*teams\.microsoft\.com/[^\s]*"""), // Microsoft Teams
            Regex("""https?://[^\s]*webex\.com/[^\s]*"""),            // Cisco Webex
        )

        /** Keywords that indicate a location string is a virtual meeting. */
        private val VIRTUAL_KEYWORDS = listOf(
            "zoom.us", "meet.google", "teams.microsoft", "webex.com",
            "whereby.com", "around.co",
        )
    }
}

data class CalendarSyncResult(
    val events: List<CalendarEventSummary> = emptyList(),
    val deletedEventIds: List<String> = emptyList(),
    val nextSyncToken: String? = null,
    val tokenExpired: Boolean = false,
) {
    companion object {
        fun empty() = CalendarSyncResult()
    }
}
