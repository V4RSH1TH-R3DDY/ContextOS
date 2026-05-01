package com.contextos.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_memory",
    indices = [Index(value = ["day_of_week", "time_slot"], unique = true)],
)
data class RoutineMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "time_slot") val timeSlot: String,       // "HH:MM" rounded to 30-min blocks
    val expectedActivity: String,
    val confidence: Float,
    val observationCount: Int,
    val lastObservedMs: Long,
)
