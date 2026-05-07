package com.contextos.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores activity context received from a paired Galaxy Watch
 * via the Wearable Data Layer API.
 *
 * Phase 11.1 — Galaxy Watch Integration (Activity Context)
 */
@Entity(tableName = "wearable_context")
data class WearableContextEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    @ColumnInfo(name = "activity_type") val activityType: String,   // walking, running, stationary, in_vehicle
    @ColumnInfo(name = "heart_rate") val heartRate: Int?,
    @ColumnInfo(name = "step_count_delta") val stepCountDelta: Int,
    @ColumnInfo(name = "device_connected") val deviceConnected: Boolean,
)
