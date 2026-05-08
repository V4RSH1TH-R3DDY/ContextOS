package com.contextos.core.service.agent.openclaw

import android.util.Log
import com.contextos.core.data.db.dao.CalendarEventCacheDao
import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.network.CalendarApiClient
import com.contextos.core.network.DriveApiClient
import com.contextos.core.network.GmailApiClient
import com.contextos.core.network.GoogleAuthManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a compact, read-only Google services context block for OpenClaw chat.
 *
 * Calendar uses the existing Room cache to protect the Calendar API call budget.
 * Gmail and Drive are queried only when the latest user turn asks for mail,
 * documents, or files.
 */
@Singleton
class GoogleServicesContextProvider @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val calendarDao: CalendarEventCacheDao,
    private val calendarApiClient: CalendarApiClient,
    private val gmailApiClient: GmailApiClient,
    private val driveApiClient: DriveApiClient,
) {

    suspend fun buildForChat(history: List<ChatTurn>): String {
        val latestUserText = history.lastOrNull { it.role == "user" }?.content.orEmpty()
        val lower = latestUserText.lowercase(Locale.US)
        val wantsAnyGoogleService = GOOGLE_SERVICE_TERMS.any { lower.contains(it) }
        val wantsCalendar = wantsAnyGoogleService || CALENDAR_TERMS.any { lower.contains(it) }
        val wantsGmail = wantsAnyGoogleService || MAIL_TERMS.any { lower.contains(it) }
        val wantsDrive = wantsAnyGoogleService || DRIVE_TERMS.any { lower.contains(it) }

        if (!wantsCalendar && !wantsGmail && !wantsDrive) {
            return ""
        }

        if (!authManager.isSignedIn()) {
            return "Google services: not connected. Ask the user to connect Google in Settings before using Calendar, Gmail, or Drive context."
        }

        if (!authManager.hasRequiredScopes()) {
            return "Google services: connected, but Calendar/Gmail/Drive full scopes have not been granted yet. Ask the user to reconnect Google."
        }

        val sections = mutableListOf<String>()
        sections += "Google services: connected with full Calendar, Gmail, and Drive access."

        if (wantsCalendar) {
            sections += buildCalendarContext(allowLiveFetch = true)
        }
        if (wantsGmail) {
            sections += buildGmailContext(latestUserText, useRecentSample = wantsAnyGoogleService)
        }
        if (wantsDrive) {
            sections += buildDriveContext(latestUserText, useRecentSample = wantsAnyGoogleService)
        }

        return sections
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
            .take(MAX_CONTEXT_CHARS)
    }

    private suspend fun buildCalendarContext(allowLiveFetch: Boolean): String {
        val now = System.currentTimeMillis()
        val events = try {
            calendarDao.getUpcomingEvents(afterMs = now).take(MAX_CALENDAR_EVENTS)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load calendar context", e)
            emptyList()
        }

        val hasFreshCache = events.any { now - it.lastFetched <= MAX_CALENDAR_CACHE_AGE_MS }
        if (events.isNotEmpty() && hasFreshCache) {
            return buildString {
                appendLine("Google Calendar cached upcoming events:")
                events.forEachIndexed { index, event ->
                    append("- ${index + 1}. ${event.title.ifBlank { "Untitled event" }}")
                    append(" at ${formatTime(event.startTime)}")
                    event.location?.takeIf { it.isNotBlank() }?.let { append(" | location: $it") }
                    event.meetingLink?.takeIf { it.isNotBlank() }?.let { append(" | meeting link available") }
                    if (event.attendeesJson.isNotBlank() && event.attendeesJson != "[]") {
                        append(" | attendees available")
                    }
                    appendLine()
                }
            }.trim()
        }

        if (!allowLiveFetch) {
            return if (events.isEmpty()) {
                "Google Calendar: no cached upcoming events."
            } else {
                "Google Calendar: cached upcoming events are stale."
            }
        }

        val liveEvents = try {
            calendarApiClient.getUpcomingEvents(hoursAhead = LIVE_CALENDAR_HOURS).take(MAX_CALENDAR_EVENTS)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load live Calendar context", e)
            emptyList()
        }

        if (liveEvents.isEmpty()) {
            return if (events.isEmpty()) {
                "Google Calendar: no upcoming events returned by the live Calendar API."
            } else {
                "Google Calendar cached upcoming events are stale, and the live Calendar API returned no newer events."
            }
        }

        return formatCalendarSummaries("Google Calendar live upcoming events:", liveEvents)
    }

    private suspend fun buildGmailContext(userText: String, useRecentSample: Boolean): String {
        val query = if (useRecentSample) "" else buildSearchTerms(userText)
        val messages = try {
            if (query.isBlank()) {
                gmailApiClient.getRecentMessages(maxResults = MAX_GMAIL_RESULTS.toLong())
            } else {
                gmailApiClient.searchMessages(query = query, maxResults = MAX_GMAIL_RESULTS.toLong())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load Gmail context", e)
            emptyList()
        }

        if (messages.isEmpty()) {
            return if (query.isBlank()) {
                "Gmail: no recent inbox messages returned."
            } else {
                "Gmail: no messages matched \"$query\"."
            }
        }

        return buildString {
            appendLine(if (query.isBlank()) "Gmail recent inbox messages:" else "Gmail messages matching \"$query\":")
            messages.take(MAX_GMAIL_RESULTS).forEachIndexed { index, message ->
                val headers = message.payload?.headers.orEmpty().associateBy { it.name.lowercase(Locale.US) }
                val subject = headers["subject"]?.value?.takeIf { it.isNotBlank() } ?: "(no subject)"
                val from = headers["from"]?.value?.takeIf { it.isNotBlank() } ?: "unknown sender"
                val date = headers["date"]?.value?.takeIf { it.isNotBlank() } ?: "unknown date"
                val snippet = message.snippet?.takeIf { it.isNotBlank() }?.let { " | ${it.take(120)}" }.orEmpty()
                appendLine("- ${index + 1}. $subject | from: $from | date: $date | link: https://mail.google.com/mail/u/0/#inbox/${message.id}$snippet")
            }
        }.trim()
    }

    private suspend fun buildDriveContext(userText: String, useRecentSample: Boolean): String {
        val terms = if (useRecentSample) emptyList() else extractSearchTerms(userText).take(MAX_SEARCH_TERMS)
        val files = try {
            if (terms.isEmpty()) {
                driveApiClient.listRecentFiles(maxResults = MAX_DRIVE_RESULTS)
            } else {
                driveApiClient.searchFiles(query = buildDriveQuery(terms), maxResults = MAX_DRIVE_RESULTS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load Drive context", e)
            emptyList()
        }

        if (files.isEmpty()) {
            return if (terms.isEmpty()) {
                "Google Drive: no recent files returned."
            } else {
                "Google Drive: no files matched ${terms.joinToString(", ")}."
            }
        }

        return buildString {
            appendLine(if (terms.isEmpty()) "Google Drive recent files:" else "Google Drive files matching ${terms.joinToString(", ")}:")
            files.take(MAX_DRIVE_RESULTS).forEachIndexed { index, file ->
                val link = file.webViewLink?.takeIf { it.isNotBlank() }?.let { " | link: $it" }.orEmpty()
                appendLine("- ${index + 1}. ${file.name ?: "Untitled file"} | ${file.mimeType ?: "unknown type"}$link")
            }
        }.trim()
    }

    private fun buildSearchTerms(text: String): String =
        extractSearchTerms(text)
            .take(MAX_SEARCH_TERMS)
            .joinToString(" ")

    private fun extractSearchTerms(text: String): List<String> =
        WORD_REGEX.findAll(text.lowercase(Locale.US))
            .map { it.value }
            .filter { it.length >= 3 && it !in STOP_WORDS && it !in MAIL_TERMS && it !in DRIVE_TERMS }
            .distinct()
            .toList()

    private fun buildDriveQuery(terms: List<String>): String =
        terms.joinToString(" or ") { term ->
            "name contains '${term.replace("'", "\\'")}'"
        }

    private fun formatCalendarSummaries(
        title: String,
        events: List<CalendarEventSummary>,
    ): String = buildString {
        appendLine(title)
        events.forEachIndexed { index, event ->
            append("- ${index + 1}. ${event.title.ifBlank { "Untitled event" }}")
            append(" at ${formatTime(event.startTime)}")
            event.location?.takeIf { it.isNotBlank() }?.let { append(" | location: $it") }
            event.meetingLink?.takeIf { it.isNotBlank() }?.let { append(" | meeting link available") }
            if (event.attendees.isNotEmpty()) {
                append(" | attendees available")
            }
            appendLine()
        }
    }.trim()

    private fun formatTime(epochMs: Long): String =
        TIME_FORMAT.format(Date(epochMs))

    private companion object {
        private const val TAG = "GoogleServicesContext"
        private const val MAX_CONTEXT_CHARS = 4_000
        private const val MAX_CALENDAR_EVENTS = 4
        private const val MAX_CALENDAR_CACHE_AGE_MS = 10L * 60 * 1_000
        private const val LIVE_CALENDAR_HOURS = 8
        private const val MAX_GMAIL_RESULTS = 3
        private const val MAX_DRIVE_RESULTS = 3
        private const val MAX_SEARCH_TERMS = 5

        private val TIME_FORMAT = SimpleDateFormat("EEE MMM d, h:mm a", Locale.US)
        private val WORD_REGEX = Regex("[a-z0-9][a-z0-9_-]*")

        private val GOOGLE_SERVICE_TERMS = setOf("google services", "google account", "google access", "connected services")
        private val CALENDAR_TERMS = setOf("calendar", "calender", "schedule", "event", "events", "meeting", "meetings", "busy", "availability")
        private val MAIL_TERMS = setOf("email", "emails", "gmail", "inbox", "mail", "message", "messages", "thread")
        private val DRIVE_TERMS = setOf("drive", "drivee", "doc", "docs", "document", "documents", "file", "files", "slides", "sheet", "sheets", "pdf", "notes")
        private val STOP_WORDS = setOf(
            "about", "access", "after", "again", "before", "calendar", "calender", "could", "find", "from",
            "google", "have", "latest", "look", "need", "please", "proper", "recent", "search", "services",
            "show", "that", "their", "there", "these", "this", "today", "what",
            "when", "where", "with", "would", "your"
        )
    }
}
