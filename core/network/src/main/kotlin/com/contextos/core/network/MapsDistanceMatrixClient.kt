package com.contextos.core.network

import android.util.Log
import com.contextos.core.data.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result returned by [MapsDistanceMatrixClient.getEstimatedTravelTime].
 *
 * @property durationSeconds      Estimated travel duration in seconds.
 * @property durationInTraffic    Duration accounting for current traffic, in seconds. Null if unavailable.
 * @property estimatedArrivalMs   Epoch millis of the projected arrival time.
 */
data class TravelTimeResult(
    val durationSeconds: Long,
    val durationInTraffic: Long?,
    val estimatedArrivalMs: Long,
)

/**
 * Client for the Google Maps Distance Matrix API.
 *
 * Used by skills that need to recommend departure reminders or navigation launch timing.
 * Results are cached for 10 minutes since traffic conditions don't change faster than that.
 */
@Singleton
class MapsDistanceMatrixClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    private val cache = mutableMapOf<String, CacheEntry>()

    /**
     * Returns the estimated travel duration and projected arrival time.
     *
     * @param originLat          WGS-84 latitude of the user's current position.
     * @param originLng          WGS-84 longitude of the user's current position.
     * @param destinationAddress Free-form address or place name of the destination.
     * @param departureTimeMs    Epoch millis of the desired departure time
     *                           (defaults to [System.currentTimeMillis]).
     * @return [TravelTimeResult] on success, or null if the API call fails.
     */
    suspend fun getEstimatedTravelTime(
        originLat: Double,
        originLng: Double,
        destinationAddress: String,
        departureTimeMs: Long = System.currentTimeMillis(),
    ): TravelTimeResult? {
        val cacheKey = buildCacheKey(originLat, originLng, destinationAddress)
        val now = System.currentTimeMillis()

        cache[cacheKey]?.let { entry ->
            if (now - entry.fetchedAtMs < CACHE_TTL_MS) {
                return TravelTimeResult(
                    durationSeconds = entry.durationSeconds,
                    durationInTraffic = entry.durationInTraffic,
                    estimatedArrivalMs = departureTimeMs + (entry.durationInTraffic?.times(1000)
                        ?: entry.durationSeconds * 1000),
                )
            }
        }

        return try {
            val result = fetchFromApi(originLat, originLng, destinationAddress, departureTimeMs)
            result?.let {
                cache[cacheKey] = CacheEntry(
                    durationSeconds = it.durationSeconds,
                    durationInTraffic = it.durationInTraffic,
                    fetchedAtMs = now,
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch travel time", e)
            null
        }
    }

    private suspend fun fetchFromApi(
        originLat: Double,
        originLng: Double,
        destinationAddress: String,
        departureTimeMs: Long,
    ): TravelTimeResult? = withContext(Dispatchers.IO) {
        val origin = "$originLat,$originLng"
        val encodedDest = URLEncoder.encode(destinationAddress, StandardCharsets.UTF_8.toString())
        val departureTime = TimeUnit.MILLISECONDS.toSeconds(departureTimeMs)

        val url = StringBuilder().apply {
            append("https://maps.googleapis.com/maps/api/distancematrix/json?")
            append("origins=").append(origin)
            append("&destinations=").append(encodedDest)
            append("&mode=driving")
            append("&traffic_model=best_guess")
            append("&departure_time=").append(departureTime)
            append("&key=").append(BuildConfig.MAPS_API_KEY)
        }.toString()

        val request = Request.Builder().url(url).build()

        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext null

        if (!response.isSuccessful) {
            Log.w(TAG, "Distance Matrix API returned ${response.code}: $body")
            return@withContext null
        }

        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val parsed = try {
            json.decodeFromString<DistanceMatrixResponse>(body)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Distance Matrix response", e)
            return@withContext null
        }

        if (parsed.status != "OK" || parsed.rows.isEmpty()) {
            Log.w(TAG, "Distance Matrix API status: ${parsed.status}")
            return@withContext null
        }

        val element = parsed.rows.first().elements.firstOrNull()
            ?: return@withContext null

        if (element.status != "OK") {
            Log.w(TAG, "Distance Matrix element status: ${element.status}")
            return@withContext null
        }

        val durationSeconds = element.duration.value
        val durationInTraffic = element.durationInTraffic?.value

        TravelTimeResult(
            durationSeconds = durationSeconds,
            durationInTraffic = durationInTraffic,
            estimatedArrivalMs = departureTimeMs + (durationInTraffic ?: durationSeconds) * 1000,
        )
    }

    fun clearCache() {
        cache.clear()
    }

    private fun buildCacheKey(lat: Double, lng: Double, dest: String): String =
        "${lat}_${lng}_${dest}"

    companion object {
        private const val TAG = "MapsDistanceMatrix"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Serialization models for the Distance Matrix API response
// ─────────────────────────────────────────────────────────────────────────────

@kotlinx.serialization.Serializable
internal data class DistanceMatrixResponse(
    val status: String,
    val rows: List<Row>,
)

@kotlinx.serialization.Serializable
internal data class Row(
    val elements: List<Element>,
)

@kotlinx.serialization.Serializable
internal data class Element(
    val status: String,
    val duration: Value,
    val distance: Value? = null,
    val durationInTraffic: Value? = null,
)

@kotlinx.serialization.Serializable
internal data class Value(
    val text: String,
    val value: Long,
)

/**
 * Internal cache entry storing a previously fetched travel time result.
 */
private data class CacheEntry(
    val durationSeconds: Long,
    val durationInTraffic: Long?,
    val fetchedAtMs: Long,
)
