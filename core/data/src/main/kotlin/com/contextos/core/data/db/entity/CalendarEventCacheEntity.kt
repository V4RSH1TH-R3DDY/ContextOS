package com.contextos.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_event_cache")
data class CalendarEventCacheEntity(
    @PrimaryKey val eventId: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val location: String?,
    val attendeesJson: String,   // JSON array of emails
    val meetingLink: String?,
    val isVirtual: Boolean,
    val lastFetched: Long,       // epoch millis
)
