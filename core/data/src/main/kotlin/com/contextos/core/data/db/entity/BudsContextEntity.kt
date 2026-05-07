package com.contextos.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores audio context received from paired Galaxy Buds
 * via the Samsung Accessory SDK.
 *
 * Phase 11.2 — Galaxy Buds Integration (Audio Context)
 */
@Entity(tableName = "buds_context")
data class BudsContextEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    @ColumnInfo(name = "buds_in_ear") val budsInEar: String,           // BOTH, ONE, NEITHER
    @ColumnInfo(name = "anc_active") val ancActive: Boolean,
    @ColumnInfo(name = "ambient_sound_active") val ambientSoundActive: Boolean,
    @ColumnInfo(name = "device_connected") val deviceConnected: Boolean,
)

/**
 * Represents the wearing state of Galaxy Buds.
 */
enum class BudsWearState {
    BOTH, ONE, NEITHER
}
