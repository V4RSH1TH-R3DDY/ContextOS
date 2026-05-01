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
        TODO("Phase 3.4 — LocationMemoryManager.recordVisit()")
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
        TODO("Phase 3.4 — LocationMemoryManager.getLabelForLocation()")
    }

    /**
     * Returns all location entries that have been assigned a non-"Unknown" label,
     * ordered by visit count descending.
     */
    suspend fun getLabelledLocations(): List<LocationMemoryEntity> {
        return dao.getLabelledLocations()
    }
}
