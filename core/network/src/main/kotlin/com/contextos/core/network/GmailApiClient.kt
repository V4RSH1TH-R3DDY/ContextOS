package com.contextos.core.network

import android.util.Log
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
 * Phase 0.2 delivers: an authenticated call that can list recent inbox messages.
 * Full message-parsing logic (for context-aware drafts) is added in Phase 3.5.
 */
@Singleton
class GmailApiClient @Inject constructor(
    private val authManager: GoogleAuthManager,
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
                    service.users().messages().list(USER_ME)
                        .setMaxResults(maxResults)
                        .setLabelIds(listOf(LABEL_INBOX))
                        .setFields("messages(id,threadId,snippet,labelIds)")
                        .execute()
                        .messages
                        ?: emptyList()
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "User needs to re-authorize Gmail access", e)
                emptyList()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to fetch Gmail messages after retries", e)
                emptyList()
            }
        }

    /**
     * Fetches the full content of a single message by [messageId].
     * Used by Phase 3.5 (LLM Message Drafting) to read message bodies.
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
                        .execute()
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "User needs to re-authorize Gmail access", e)
                null
            } catch (e: IOException) {
                Log.e(TAG, "Failed to fetch message $messageId after retries", e)
                null
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
