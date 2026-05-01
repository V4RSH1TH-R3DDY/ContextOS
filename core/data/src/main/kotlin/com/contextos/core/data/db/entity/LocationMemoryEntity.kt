package com.contextos.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_memory")
data class LocationMemoryEntity(
    @PrimaryKey val latLngHash: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val inferredLabel: String,          // "Home", "Office", "Gym", "Unknown", etc.
    val visitCount: Int,
    val typicalArrivalTime: String?,    // "HH:MM" or null
    val typicalDepartureTime: String?,
    val lastVisitedMs: Long,
)
