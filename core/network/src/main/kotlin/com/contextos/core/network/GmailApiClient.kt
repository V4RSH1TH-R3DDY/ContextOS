package com.contextos.core.network

import android.util.Log
import com.contextos.core.data.preferences.PreferencesManager
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authenticated client for the Gmail API (v1).
 *
 * Scope used: [GoogleAuthManager.SCOPE_GMAIL] (readonly).
 * Rate limit budget: Gmail allows 1B quota units/day. A metadata list call
 * costs ~5 units, so 4 calls per cycle × 96 cycles/day ≈ 1,920 units — well
 * within budget.
 *
 * Returns hydrated message metadata/snippets so downstream context can show
 * subjects, senders, dates, snippets, and stable Gmail deep links.
 */
@Singleton
class GmailApiClient @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val preferencesManager: PreferencesManager,
) {

    // ─── Service builder ─────────────────────────────────────────────────────

    private fun buildService(): Gmail? {
        val credential = authManager.getCredential(listOf(GoogleAuthManager.SCOPE_GMAIL))
            ?: return null
        return Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
            .setApplicationName(APP_NAME)
            .build()
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns up to [maxResults] messages from the user's inbox, metadata only
     * (id, threadId, snippet, labelIds). Full message bodies are not fetched here.
     *
     * Returns an empty list on auth errors or after all retries are exhausted.
     */
    suspend fun getRecentMessages(maxResults: Long = 10): List<Message> =
        withContext(Dispatchers.IO) {
            val service = buildService()
                ?: return@withContext emptyList<Message>().also {
                    Log.w(TAG, "getRecentMessages: not signed in")
                }

            try {
                retryWithBackoff(tag = TAG) {
                    val messageRefs = service.users().messages().list(USER_ME)
                        .setMaxResults(maxResults)
                        .setLabelIds(listOf(LABEL_INBOX))
                        .setFields("messages(id,threadId),nextPageToken")
                        .execute()
                        .messages
                        ?: emptyList()
                    hydrateMessages(service, messageRefs)
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "User needs to re-authorize Gmail access", e)
                preferencesManager.setGoogleReauthRequired(true)
                emptyList()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to fetch Gmail messages after retries", e)
                emptyList()
            }
        }

    /**
     * Searches the user's inbox messages using the specified [query].
     * Returns up to [maxResults] messages (metadata only).
     */
    suspend fun searchMessages(query: String, maxResults: Long = 10): List<Message> =
        withContext(Dispatchers.IO) {
            val service = buildService()
                ?: return@withContext emptyList<Message>().also {
                    Log.w(TAG, "searchMessages: not signed in")
                }

            try {
                retryWithBackoff(tag = TAG) {
                    val messageRefs = service.users().messages().list(USER_ME)
                        .setQ(query)
                        .setMaxResults(maxResults)
                        .setFields("messages(id,threadId),nextPageToken")
                        .execute()
                        .messages
                        ?: emptyList()
                    hydrateMessages(service, messageRefs)
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "User needs to re-authorize Gmail access", e)
                preferencesManager.setGoogleReauthRequired(true)
                emptyList()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to search Gmail messages after retries", e)
                emptyList()
            }
        }

    /**
     * Fetches readable metadata and snippet for a single message by [messageId].
     *
     * Returns null if the message cannot be fetched.
     */
    suspend fun getMessage(messageId: String): Message? =
        withContext(Dispatchers.IO) {
            val service = buildService()
                ?: return@withContext null

            try {
                retryWithBackoff(tag = TAG) {
                    service.users().messages().get(USER_ME, messageId)
                        .setFormat("metadata")
                        .setMetadataHeaders(listOf("From", "To", "Subject", "Date"))
                        .setFields("id,threadId,snippet,labelIds,payload/headers")
                        .execute()
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "User needs to re-authorize Gmail access", e)
                preferencesManager.setGoogleReauthRequired(true)
                null
            } catch (e: IOException) {
                Log.e(TAG, "Failed to fetch message $messageId after retries", e)
                null
            }
        }

    private fun hydrateMessages(service: Gmail, messageRefs: List<Message>): List<Message> {
        return messageRefs.mapNotNull { messageRef ->
            val id = messageRef.id ?: return@mapNotNull null
            service.users().messages().get(USER_ME, id)
                .setFormat("metadata")
                .setMetadataHeaders(listOf("From", "To", "Subject", "Date"))
                .setFields("id,threadId,snippet,labelIds,payload/headers")
                .execute()
        }
    }

    // ─── Constants ───────────────────────────────────────────────────────────

    companion object {
        private const val TAG       = "GmailApiClient"
        private const val APP_NAME  = "ContextOS"
        private const val USER_ME   = "me"
        private const val LABEL_INBOX = "INBOX"

        private val HTTP_TRANSPORT = NetHttpTransport()
        private val JSON_FACTORY   = GsonFactory.getDefaultInstance()
    }
}
