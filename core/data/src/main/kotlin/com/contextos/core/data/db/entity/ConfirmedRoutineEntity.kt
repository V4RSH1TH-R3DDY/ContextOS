package com.contextos.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "confirmed_routine",
    indices = [Index(value = ["day_of_week", "time_slot_start"], unique = true)]
)
data class ConfirmedRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_type") val routineType: String,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "time_slot_start") val timeSlotStart: Int,
    @ColumnInfo(name = "time_slot_end") val timeSlotEnd: Int,
    val confidence: Float,
    @ColumnInfo(name = "observation_count") val observationCount: Int,
    @ColumnInfo(name = "last_observed") val lastObservedMs: Long,
    @ColumnInfo(name = "suggested_action") val suggestedAction: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
)
