package com.contextos.core.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result returned by [MapsDistanceMatrixClient.getEstimatedTravelTime].
 *
 * @property durationSeconds      Estimated travel duration in seconds.
 * @property estimatedArrivalMs   Epoch millis of the projected arrival time.
 */
data class TravelTimeResult(
    val durationSeconds: Long,
    val estimatedArrivalMs: Long,
)

/**
 * Client for the Google Maps Distance Matrix API.
 *
 * Used by the [com.contextos.core.skills.Skill] implementations that need to
 * recommend departure reminders or navigation launch timing.
 *
 * Full implementation: Phase 2.4
 */
@Singleton
class MapsDistanceMatrixClient @Inject constructor(
    private val authManager: GoogleAuthManager,
) {
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
        TODO("Phase 2.4 — MapsDistanceMatrixClient.getEstimatedTravelTime()")
    }
}
