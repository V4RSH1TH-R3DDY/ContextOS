package com.contextos.core.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.model.ActionOutcome
import com.contextos.core.data.model.LatLng
import com.contextos.core.data.model.SituationModel
import com.contextos.core.network.MapsDistanceMatrixClient
import com.contextos.core.network.TravelTimeResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Navigation Launcher skill — offers to launch Google Maps navigation to
 * upcoming calendar event locations.
 *
 * Trigger conditions:
 *  - Next calendar event has a non-empty location field
 *  - Event starts within 30 minutes
 *  - Current GPS location is not already at the event location (within 500 m radius)
 *  - It is not a virtual/online meeting (no meeting link present)
 *
 * Execution:
 *  - Constructs a google.navigation:q=[location]&mode=d intent
 *  - Optionally fetches estimated travel time from the Distance Matrix API
 *  - Auto-launches if user has auto-approve enabled, otherwise returns PendingConfirmation
 *  - Includes estimated arrival in the action log entry
 */
@Singleton
class NavigationLauncherSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapsClient: MapsDistanceMatrixClient,
    private val userPreferenceDao: UserPreferenceDao,
) : Skill {

    override val id: String = "navigation_launcher"
    override val name: String = "Navigation Launcher"
    override val description: String = "Launches Google Maps navigation to your next in-person meeting."

    override fun shouldTrigger(model: SituationModel): Boolean {
        val event = model.nextCalendarEvent ?: return false

        if (event.location.isNullOrEmpty()) return false

        if (event.isVirtual || !event.meetingLink.isNullOrEmpty()) return false

        val nowMs = model.currentTime
        val minutesUntilStart = TimeUnit.MILLISECONDS.toMinutes(event.startTime - nowMs)
        if (minutesUntilStart !in 0..30) return false

        val currentLocation = model.currentLocation ?: return true

        val distanceMeters = haversineDistance(
            lat1 = currentLocation.latitude,
            lon1 = currentLocation.longitude,
            lat2 = 0.0,
            lon2 = 0.0,
        )

        return distanceMeters > ALREADY_AT_DESTINATION_RADIUS_M
    }

    override suspend fun execute(model: SituationModel): SkillResult = withContext(Dispatchers.Default) {
        val event = model.nextCalendarEvent ?: return@withContext SkillResult.Skipped("No calendar event found")
        val location = event.location ?: return@withContext SkillResult.Skipped("Event has no location")

        if (event.isVirtual || !event.meetingLink.isNullOrEmpty()) {
            return@withContext SkillResult.Skipped("Event is virtual — navigation not needed")
        }

        val nowMs = model.currentTime
        val minutesUntilStart = TimeUnit.MILLISECONDS.toMinutes(event.startTime - nowMs)
        if (minutesUntilStart < 0) {
            return@withContext SkillResult.Skipped("Event already started")
        }

        val travelInfo = model.currentLocation?.let { loc ->
            mapsClient.getEstimatedTravelTime(
                originLat = loc.latitude,
                originLng = loc.longitude,
                destinationAddress = location,
                departureTimeMs = nowMs,
            )
        }

        val etaDescription = travelInfo?.let { info ->
            val travelMinutes = TimeUnit.SECONDS.toMinutes(info.durationInTraffic ?: info.durationSeconds)
            val arrivalMinutes = TimeUnit.MILLISECONDS.toMinutes(info.estimatedArrivalMs - nowMs)
            " ($travelMinutes min travel, arrive ${arrivalMinutes} min before start)"
        } ?: ""

        val prompt = "Launch navigation to '${event.title}' at $location$etaDescription"

        val userPref = userPreferenceDao.getBySkillId(id)
        val shouldAutoApprove = userPref?.autoApprove ?: false

        return@withContext if (shouldAutoApprove) {
            try {
                launchNavigation(location)
                SkillResult.Success(
                    description = "Launched navigation to $location for '${event.title}'$etaDescription",
                    outcome = ActionOutcome.SUCCESS,
                )
            } catch (e: Exception) {
                SkillResult.Failure(
                    description = "Failed to launch navigation: ${e.message}",
                    error = e,
                    outcome = ActionOutcome.FAILURE,
                )
            }
        } else {
            SkillResult.PendingConfirmation(
                description = prompt,
                confirmationMessage = "Open Google Maps for navigation to $location?",
                pendingAction = {
                    try {
                        launchNavigation(location)
                        SkillResult.Success(
                            description = "Launched navigation to $location for '${event.title}'$etaDescription",
                            outcome = ActionOutcome.SUCCESS,
                        )
                    } catch (e: Exception) {
                        SkillResult.Failure(
                            description = "Failed to launch navigation: ${e.message}",
                            error = e,
                            outcome = ActionOutcome.FAILURE,
                        )
                    }
                },
            )
        }
    }

    private fun launchNavigation(destination: String) {
        val encodedDestination = Uri.encode(destination)
        val intent = Intent(
            Intent.ACTION_VIEW,
            "google.navigation:q=$encodedDestination&mode=d".toUri(),
        ).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        private const val ALREADY_AT_DESTINATION_RADIUS_M = 500.0

        /**
         * Haversine distance between two lat/lng points in meters.
         * Used for a rough proximity check — sufficient for the 500 m radius.
         */
        private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            if (lat1 == lat2 && lon1 == lon2) return 0.0
            if (lat2 == 0.0 && lon2 == 0.0) return Double.MAX_VALUE

            val earthRadiusM = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).let { it * it }
            return earthRadiusM * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
        }
    }
}
