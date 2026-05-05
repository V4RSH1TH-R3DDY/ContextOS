package com.contextos.core.memory

import com.contextos.core.data.db.dao.LocationMemoryDao
import com.contextos.core.data.db.entity.LocationMemoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the location-memory subsystem.
 *
 * Clusters GPS coordinates into frequently visited places and assigns
 * human-readable labels (e.g., "Home", "Office", "Gym") based on visit
 * patterns, dwell time, and arrival/departure times.
 *
 * Full implementation: Phase 3.4 — Location Memory System
 */
@Singleton
class LocationMemoryManager @Inject constructor(
    private val dao: LocationMemoryDao,
) {
    /**
     * Records a visit to the given GPS coordinate, merging it into an existing
     * cluster or creating a new location entry if no nearby cluster exists.
     *
     * @param latitude  WGS-84 latitude of the observed position.
     * @param longitude WGS-84 longitude of the observed position.
     */
    suspend fun recordVisit(latitude: Double, longitude: Double) {
        val latLngHash = String.format(java.util.Locale.US, "%.3f,%.3f", latitude, longitude)
        val existing = dao.getByHash(latLngHash)
        val now = System.currentTimeMillis()
        
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekday = dayOfWeek in java.util.Calendar.MONDAY..java.util.Calendar.FRIDAY

        if (existing != null) {
            val newVisitCount = existing.visitCount + 1
            var newLabel = existing.inferredLabel
            if (newVisitCount >= 5) {
                if (isWeekday && hour in 9..17) {
                    newLabel = "Office"
                } else if (hour >= 20 || hour <= 8) {
                    newLabel = "Home"
                } else if (newVisitCount >= 10 && newLabel == "Unknown") {
                    newLabel = "Frequent Location"
                }
            }
            
            dao.upsert(existing.copy(
                visitCount = newVisitCount,
                inferredLabel = newLabel,
                lastVisitedMs = now
            ))
        } else {
            dao.upsert(LocationMemoryEntity(
                latLngHash = latLngHash,
                centerLatitude = latitude,
                centerLongitude = longitude,
                inferredLabel = "Unknown",
                visitCount = 1,
                typicalArrivalTime = null,
                typicalDepartureTime = null,
                lastVisitedMs = now
            ))
        }
    }

    /**
     * Returns the inferred label for the given coordinate by looking up the
     * nearest known cluster in the database.
     *
     * @param latitude  WGS-84 latitude to look up.
     * @param longitude WGS-84 longitude to look up.
     * @return Human-readable label, or "Unknown" if no cluster matches.
     */
    suspend fun getLabelForLocation(latitude: Double, longitude: Double): String {
        val latLngHash = String.format(java.util.Locale.US, "%.3f,%.3f", latitude, longitude)
        val entity = dao.getByHash(latLngHash)
        return entity?.inferredLabel ?: "Unknown"
    }

    /**
     * Returns all location entries that have been assigned a non-"Unknown" label,
     * ordered by visit count descending.
     */
    suspend fun getLabelledLocations(): List<LocationMemoryEntity> {
        return dao.getLabelledLocations()
    }
}