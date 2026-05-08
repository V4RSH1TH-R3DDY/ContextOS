package com.contextos.core.network

import android.util.Log
import com.contextos.core.data.preferences.PreferencesManager
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
 * Scope used: [GoogleAuthManager.SCOPE_DRIVE] (full read/write access).
 * Rate limit budget: Drive allows 1B quota units/day. A file list call
 * costs ~1 unit, so this is essentially unlimited for our usage pattern.
 *
 * Read operations: [listRecentFiles], [searchFiles].
 * Write operations: [createFolder], [moveFile].
 */
@Singleton
class DriveApiClient @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val preferencesManager: PreferencesManager,
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
                preferencesManager.setGoogleReauthRequired(true)
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
                preferencesManager.setGoogleReauthRequired(true)
                emptyList()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to search Drive files after retries", e)
                emptyList()
            }
        }

    // ─── Write Operations ─────────────────────────────────────────────────────

    /**
     * Creates a new folder in Drive. Returns the created folder metadata
     * (id, name, mimeType, webViewLink) or null on failure.
     *
     * @param name  Display name for the folder.
     * @param parentFolderId  Optional parent folder ID. Root Drive if null.
     */
    suspend fun createFolder(name: String, parentFolderId: String? = null): File? =
        withContext(Dispatchers.IO) {
            val service = buildService()
                ?: return@withContext null.also {
                    Log.w(TAG, "createFolder: not signed in")
                }

            try {
                retryWithBackoff(tag = TAG) {
                    val metadata = File()
                        .setName(name)
                        .setMimeType(FOLDER_MIME_TYPE)
                    parentFolderId?.let { metadata.setParents(listOf(it)) }
                    service.files().create(metadata)
                        .setFields("id,name,mimeType,webViewLink")
                        .execute()
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "User needs to re-authorize Drive access", e)
                preferencesManager.setGoogleReauthRequired(true)
                null
            } catch (e: IOException) {
                Log.e(TAG, "Failed to create Drive folder after retries", e)
                null
            }
        }

    /**
     * Moves a file/folder into a different parent folder.
     *
     * @param fileId  The ID of the file/folder to move.
     * @param newParentFolderId  The target folder ID.
     * @param oldParentFolderId  Optional — if null the API removes all current parents.
     */
    suspend fun moveFile(
        fileId: String,
        newParentFolderId: String,
        oldParentFolderId: String? = null,
    ): File? = withContext(Dispatchers.IO) {
        val service = buildService()
            ?: return@withContext null.also {
                Log.w(TAG, "moveFile: not signed in")
            }

        try {
            retryWithBackoff(tag = TAG) {
                val removeParents = oldParentFolderId ?: run {
                    service.files().get(fileId)
                        .setFields("parents")
                        .execute()
                        .parents
                        ?.joinToString(",")
                        ?: ""
                }
                val update = service.files().update(fileId, null)
                    .setAddParents(newParentFolderId)
                if (removeParents.isNotBlank()) {
                    update.setRemoveParents(removeParents)
                }
                update.setFields("id,name,mimeType,parents,webViewLink")
                    .execute()
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "User needs to re-authorize Drive access", e)
            preferencesManager.setGoogleReauthRequired(true)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Failed to move Drive file after retries", e)
            null
        }
    }

    // ─── Constants ───────────────────────────────────────────────────────────

    companion object {
        private const val TAG             = "DriveApiClient"
        private const val APP_NAME        = "ContextOS"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

        private val HTTP_TRANSPORT = NetHttpTransport()
        private val JSON_FACTORY   = GsonFactory.getDefaultInstance()
    }
}
