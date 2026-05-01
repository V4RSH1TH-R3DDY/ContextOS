package com.contextos.core.network

import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authenticated client for the Google Drive API (v3).
 *
 * Scope used: [GoogleAuthManager.SCOPE_DRIVE] (readonly).
 * Rate limit budget: Drive allows 1B quota units/day. A file list call
 * costs ~1 unit, so this is essentially unlimited for our usage pattern.
 *
 * Phase 0.2 delivers: an authenticated call that can list recent Drive files.
 * The document fetcher skill (Phase 4.2) will use [getFileContent] to pull
 * relevant files into the situation context.
 */
@Singleton
class DriveApiClient @Inject constructor(
    private val authManager: GoogleAuthManager,
) {

    // ─── Service builder ─────────────────────────────────────────────────────

    private fun buildService(): Drive? {
        val credential = authManager.getCredential(listOf(GoogleAuthManager.SCOPE_DRIVE))
            ?: return null
        return Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
            .setApplicationName(APP_NAME)
            .build()
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the [maxResults] most recently modified Drive files.
     * Each result includes: id, name, mimeType, modifiedTime, webViewLink.
     *
     * Returns an empty list on auth errors or after all retries are exhausted.
     */
    suspend fun listRecentFiles(maxResults: Int = 10): List<File> =
        withContext(Dispatchers.IO) {
            val service = buildService()
                ?: return@withContext emptyList<File>().also {
                    Log.w(TAG, "listRecentFiles: not signed in")
                }

            try {
                retryWithBackoff(tag = TAG) {
                    service.files().list()
                        .setPageSize(maxResults)
                        .setOrderBy("modifiedTime desc")
                        .setFields("files(id,name,mimeType,modifiedTime,webViewLink,parents)")
                        .execute()
                        .files
                        ?: emptyList()
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "User needs to re-authorize Drive access", e)
                emptyList()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to list Drive files after retries", e)
                emptyList()
            }
        }

    /**
     * Searches Drive files by [query] string using the Drive query language.
     * Example: `"name contains 'meeting notes'"` or `"mimeType = 'application/pdf'"`
     *
     * See: https://developers.google.com/drive/api/guides/search-files
     */
    suspend fun searchFiles(query: String, maxResults: Int = 10): List<File> =
        withContext(Dispatchers.IO) {
            val service = buildService()
                ?: return@withContext emptyList<File>().also {
                    Log.w(TAG, "searchFiles: not signed in")
                }

            try {
                retryWithBackoff(tag = TAG) {
                    service.files().list()
                        .setQ(query)
                        .setPageSize(maxResults)
                        .setOrderBy("modifiedTime desc")
                        .setFields("files(id,name,mimeType,modifiedTime,webViewLink)")
                        .execute()
                        .files
                        ?: emptyList()
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "User needs to re-authorize Drive access", e)
                emptyList()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to search Drive files after retries", e)
                emptyList()
            }
        }

    // ─── Constants ───────────────────────────────────────────────────────────

    companion object {
        private const val TAG      = "DriveApiClient"
        private const val APP_NAME = "ContextOS"

        private val HTTP_TRANSPORT = NetHttpTransport()
        private val JSON_FACTORY   = GsonFactory.getDefaultInstance()
    }
}
