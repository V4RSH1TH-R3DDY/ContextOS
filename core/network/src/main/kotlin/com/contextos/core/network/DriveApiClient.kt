package com.contextos.core.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the Google Drive API (v3).
 *
 * Lists recently modified files to surface context about what the user is
 * actively working on (document titles, last-modified timestamps, MIME types).
 *
 * Full implementation: Phase 0.2 — Drive Integration
 */
@Singleton
class DriveApiClient @Inject constructor(
    private val authManager: GoogleAuthManager,
) {
    /**
     * Returns a list of recently accessed Drive file metadata objects.
     *
     * @return List of raw file metadata objects; will be mapped by the calling
     *         repository. Returns an empty list until Phase 0.2.
     */
    suspend fun listRecentFiles(): List<Any> {
        TODO("Phase 0.2 — DriveApiClient.listRecentFiles()")
    }
}
