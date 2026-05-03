package com.contextos.core.network

import android.util.Log
import com.contextos.core.data.model.CalendarEventSummary
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
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
