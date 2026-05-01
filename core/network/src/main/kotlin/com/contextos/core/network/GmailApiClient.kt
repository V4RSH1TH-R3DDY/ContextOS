package com.contextos.core.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the Gmail API (v1).
 *
 * Retrieves recent message metadata (sender, subject, snippet) to provide
 * contextual awareness of the user's communication activity.
 *
 * Full implementation: Phase 0.2 — Gmail Integration
 */
@Singleton
class GmailApiClient @Inject constructor(
    private val authManager: GoogleAuthManager,
) {
    /**
     * Returns a list of recent Gmail message metadata objects.
     *
     * @return List of raw message objects; will be mapped by the calling
     *         repository. Returns an empty list until Phase 0.2.
     */
    suspend fun getRecentMessages(): List<Any> {
        TODO("Phase 0.2 — GmailApiClient.getRecentMessages()")
    }
}
