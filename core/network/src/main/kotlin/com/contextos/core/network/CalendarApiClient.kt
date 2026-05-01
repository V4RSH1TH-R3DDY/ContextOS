package com.contextos.core.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the Google Calendar API (v3).
 *
 * Fetches upcoming calendar events for the authenticated user and populates
 * the [com.contextos.core.data.db.entity.CalendarEventCacheEntity] table via
 * the repository layer.
 *
 * Full implementation: Phase 1.5 — Calendar Data Sync
 */
@Singleton
class CalendarApiClient @Inject constructor(
    private val authManager: GoogleAuthManager,
) {
    /**
     * Returns a list of upcoming calendar events starting from now.
     *
     * @param hoursAhead How many hours into the future to query (default: 8).
     * @return List of raw event objects; will be mapped to
     *         [com.contextos.core.data.db.entity.CalendarEventCacheEntity] by
     *         the calling repository. Returns an empty list until Phase 1.5.
     */
    suspend fun getUpcomingEvents(hoursAhead: Int = 8): List<Any> {
        TODO("Phase 1.5 — CalendarApiClient.getUpcomingEvents()")
    }
}
