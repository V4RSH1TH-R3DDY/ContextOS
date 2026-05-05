package com.contextos.core.service.agent

import com.contextos.core.memory.LocationMemoryManager
import com.contextos.core.memory.PreferenceMemoryManager
import com.contextos.core.memory.RoutineMemoryManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates output from all three memory managers into a short
 * natural-language summary that is included in the [SituationModel.memorySummary]
 * field and forwarded to the OpenClaw prompt.
 *
 * This gives the LLM context about the user's learned patterns so it can
 * make more personalized recommendations.
 *
 * Phase 3.1-3.4 integration point.
 */
@Singleton
class MemorySummaryBuilder @Inject constructor(
    private val routineMemory: RoutineMemoryManager,
    private val locationMemory: LocationMemoryManager,
) {

    /**
     * Builds a concise multi-line summary of what the memory layers know.
     *
     * @param dayOfWeek  ISO day-of-week (1 = Monday … 7 = Sunday).
     * @param timeSlot   "HH:MM" string rounded to the nearest 30-minute block.
     * @param latitude   Current latitude (nullable if unavailable).
     * @param longitude  Current longitude (nullable if unavailable).
     * @return A short summary string, or empty string if no relevant memory exists.
     */
    suspend fun build(
        dayOfWeek: Int,
        timeSlot: String,
        latitude: Double?,
        longitude: Double?,
    ): String {
        val parts = mutableListOf<String>()

        // ── Routine prediction ───────────────────────────────────────────
        val predicted = routineMemory.getPredictedActivity(dayOfWeek, timeSlot)
        if (predicted != null) {
            val (activity, confidence) = predicted
            val pct = "%.0f".format(confidence * 100)
            parts.add("User typically: $activity at this time ($pct% confidence).")
        }

        // ── Learned routines snapshot ────────────────────────────────────
        val routines = routineMemory.getLearnedRoutines()
        if (routines.isNotEmpty()) {
            val dayNames = arrayOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val top = routines.take(3).joinToString("; ") { r ->
                val day = dayNames.getOrElse(r.dayOfWeek) { "?" }
                "$day ${r.timeSlot} → ${r.expectedActivity}"
            }
            parts.add("Learned routines: $top.")
        }

        // ── Location label ───────────────────────────────────────────────
        if (latitude != null && longitude != null) {
            val label = locationMemory.getLabelForLocation(latitude, longitude)
            if (label != "Unknown") {
                parts.add("Current location recognized as: $label.")
            }
        }

        // ── Known places ─────────────────────────────────────────────────
        val knownPlaces = locationMemory.getLabelledLocations()
        if (knownPlaces.isNotEmpty()) {
            val names = knownPlaces.take(3).joinToString(", ") { it.inferredLabel }
            parts.add("Known places: $names.")
        }

        return parts.joinToString(" ")
    }
}
