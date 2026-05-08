package com.contextos.core.network

import android.util.Base64
import android.util.Log
import com.contextos.core.data.preferences.PreferencesManager
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.ModifyMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets
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

    // ─── Write Operations ────────────────────────────────────────────────────

    /**
     * Sends an email from the user's Gmail account.
     *
     * @param to  Recipient email address.
     * @param subject  Email subject.
     * @param body  Plain-text body.
     * @return The sent Message (including id) or null on failure.
     */
    suspend fun sendMessage(
        to: String,
        subject: String,
        body: String,
    ): Message? = withContext(Dispatchers.IO) {
        val service = buildService()
            ?: return@withContext null.also {
                Log.w(TAG, "sendMessage: not signed in")
            }

        try {
            retryWithBackoff(tag = TAG) {
                val rawBytes = buildMimeMessage(to, subject, body)
                val encoded = Base64.encodeToString(rawBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                val message = Message().setRaw(encoded)
                service.users().messages().send(USER_ME, message)
                    .setFields("id,threadId")
                    .execute()
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "User needs to re-authorize Gmail access", e)
            preferencesManager.setGoogleReauthRequired(true)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send email after retries", e)
            null
        }
    }

    /**
     * Moves a message to the Trash.
     * @return true if trashed, false on failure.
     */
    suspend fun trashMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        val service = buildService()
            ?: return@withContext false.also {
                Log.w(TAG, "trashMessage: not signed in")
            }

        try {
            retryWithBackoff(tag = TAG) {
                service.users().messages().trash(USER_ME, messageId).execute()
                true
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "User needs to re-authorize Gmail access", e)
            preferencesManager.setGoogleReauthRequired(true)
            false
        } catch (e: IOException) {
            Log.e(TAG, "Failed to trash message after retries", e)
            false
        }
    }

    /**
     * Permanently deletes a message.
     * @return true if deleted, false on failure.
     */
    suspend fun deleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        val service = buildService()
            ?: return@withContext false.also {
                Log.w(TAG, "deleteMessage: not signed in")
            }

        try {
            retryWithBackoff(tag = TAG) {
                service.users().messages().delete(USER_ME, messageId).execute()
                true
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "User needs to re-authorize Gmail access", e)
            preferencesManager.setGoogleReauthRequired(true)
            false
        } catch (e: IOException) {
            Log.e(TAG, "Failed to delete message after retries", e)
            false
        }
    }

    /**
     * Modifies labels on a message (e.g. mark read, add star, move to folder).
     *
     * @param messageId  The message to modify.
     * @param addLabelIds  Labels to add (e.g. "STARRED", "IMPORTANT").
     * @param removeLabelIds  Labels to remove (e.g. "UNREAD").
     * @return true if modified, false on failure.
     */
    suspend fun modifyMessage(
        messageId: String,
        addLabelIds: List<String>? = null,
        removeLabelIds: List<String>? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val service = buildService()
            ?: return@withContext false.also {
                Log.w(TAG, "modifyMessage: not signed in")
            }

        try {
            retryWithBackoff(tag = TAG) {
                val request = ModifyMessageRequest()
                addLabelIds?.let { request.setAddLabelIds(it) }
                removeLabelIds?.let { request.setRemoveLabelIds(it) }
                service.users().messages().modify(USER_ME, messageId, request).execute()
                true
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "User needs to re-authorize Gmail access", e)
            preferencesManager.setGoogleReauthRequired(true)
            false
        } catch (e: IOException) {
            Log.e(TAG, "Failed to modify message after retries", e)
            false
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Builds a minimal RFC 2822 message bytes for sending via Gmail API.
     * Uses CRLF line endings and base64-encodes the subject for non-ASCII safety.
     */
    private fun buildMimeMessage(to: String, subject: String, body: String): ByteArray {
        val encodedSubject = Base64.encodeToString(
            subject.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP,
        )
        val email = buildString {
            appendLine("MIME-Version: 1.0")
            appendLine("Content-Type: text/plain; charset=UTF-8")
            appendLine("Content-Transfer-Encoding: base64")
            appendLine("To: $to")
            appendLine("Subject: =?UTF-8?B?$encodedSubject?=")
            appendLine("Date: ${java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.US).format(java.util.Date())}")
            appendLine()
            val bodyEncoded = Base64.encodeToString(
                body.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP,
            )
            append(bodyEncoded)
        }
        // Convert to CRLF line endings as required by RFC 2822
        return email.toString()
            .replace("\n", "\r\n")
            .toByteArray(StandardCharsets.UTF_8)
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
