package com.contextos.core.network

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the Samsung SmartThings REST API.
 *
 * Scope requested: `r:devices:*` (read device states), `r:locations:*` (read location/presence)
 *
 * Features:
 *   - Presence detection (home/away) via SmartThings Presence Sensor
 *   - Mode detection (Home, Away, Night, Vacation)
 *   - Rate-limited polling (max once every 5 minutes)
 *   - Cached last-known presence state for resilience
 *
 * Integration with existing skills:
 *   - LocationIntelligenceSkill: supplements GPS home detection
 *   - MessageDrafterSkill: suppresses ETA drafts on home arrival
 *   - DndSetterSkill: auto-enables DND when SmartThings mode = Night
 *
 * Phase 11.3 — SmartThings Home Arrival Integration
 */
@Singleton
class SmartThingsClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val mutex = Mutex()
    private var cachedStatus: PresenceStatus? = null
    private var lastFetchTimeMs: Long = 0L

    /**
     * Returns the current presence status for the given SmartThings location.
     *
     * Rate limited to max once every 5 minutes (SmartThings API: 300 calls/15 min per token).
     * Uses cached state if API call fails.
     *
     * @param locationId The SmartThings location ID.
     * @param accessToken OAuth 2.0 access token for SmartThings API.
     * @return [PresenceStatus] or null if SmartThings is not configured.
     */
    suspend fun getPresenceStatus(
        locationId: String,
        accessToken: String,
    ): PresenceStatus? = mutex.withLock {
        val now = System.currentTimeMillis()

        // Rate limit: max once every 5 minutes
        if (now - lastFetchTimeMs < POLL_INTERVAL_MS && cachedStatus != null) {
            Log.d(TAG, "Using cached SmartThings presence: ${cachedStatus!!.mode}")
            return cachedStatus
        }

        return try {
            val status = fetchPresenceFromApi(locationId, accessToken)
            cachedStatus = status
            lastFetchTimeMs = now
            status
        } catch (e: Exception) {
            Log.w(TAG, "SmartThings API call failed — using cached state", e)
            cachedStatus // Fall back to cached state
        }
    }

    /**
     * Checks if the user is currently at home according to SmartThings.
     */
    suspend fun isUserHome(locationId: String, accessToken: String): Boolean {
        val status = getPresenceStatus(locationId, accessToken) ?: return false
        return status.isHome
    }

    /**
     * Checks if SmartThings night mode is active.
     */
    suspend fun isNightMode(locationId: String, accessToken: String): Boolean {
        val status = getPresenceStatus(locationId, accessToken) ?: return false
        return status.mode == "Night"
    }

    /**
     * Clears the cached presence status (e.g., on account disconnect).
     */
    fun clearCache() {
        cachedStatus = null
        lastFetchTimeMs = 0L
    }

    // ── Private API interaction ──────────────────────────────────────────────

    private suspend fun fetchPresenceFromApi(
        locationId: String,
        accessToken: String,
    ): PresenceStatus = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/locations/$locationId/modes/current"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/json")
            .build()

        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.string()

        if (!response.isSuccessful) {
            Log.w(TAG, "SmartThings API returned ${response.code}: $body")
            throw SmartThingsApiException("API returned ${response.code}")
        }

        // Parse the mode response
        // SmartThings returns: {"id":"...", "label":"Home|Away|Night|...", "name":"..."}
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        val mode = try {
            val parsed = json.parseToJsonElement(body ?: "{}").let {
                it as? kotlinx.serialization.json.JsonObject
            }
            parsed?.get("label")?.let {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
            } ?: "Unknown"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse SmartThings mode response", e)
            "Unknown"
        }

        val isHome = mode.equals("Home", ignoreCase = true) || mode.equals("Night", ignoreCase = true)

        PresenceStatus(isHome = isHome, mode = mode)
    }

    companion object {
        private const val TAG = "SmartThingsClient"
        private const val BASE_URL = "https://api.smartthings.com/v1"
        private const val POLL_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }
}

/**
 * SmartThings presence status.
 */
data class PresenceStatus(
    val isHome: Boolean,
    val mode: String,  // "Home", "Away", "Night", "Vacation"
)

class SmartThingsApiException(message: String) : Exception(message)
