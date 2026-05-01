package com.contextos.core.network

import android.util.Log
import com.contextos.core.data.model.CalendarEventSummary
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
        withContext(Dispatchers.IO) {
            val service = buildService()
                ?: return@withContext emptyList<CalendarEventSummary>().also {
                    Log.w(TAG, "getUpcomingEvents: not signed in")
                }

            val now   = System.currentTimeMillis()
            val until = now + hoursAhead * 3_600_000L

            try {
                retryWithBackoff(tag = TAG) {
                    val result = service.events().list("primary")
                        .setTimeMin(DateTime(now))
                        .setTimeMax(DateTime(until))
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .setMaxResults(20)
                        .execute()

                    (result.items ?: emptyList()).map { event ->
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
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "User needs to re-authorize Calendar access", e)
                emptyList()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to fetch events after retries", e)
                emptyList()
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
