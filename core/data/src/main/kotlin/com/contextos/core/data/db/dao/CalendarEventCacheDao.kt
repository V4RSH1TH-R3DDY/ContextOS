package com.contextos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.contextos.core.data.db.entity.CalendarEventCacheEntity

@Dao
interface CalendarEventCacheDao {

    @Upsert
    suspend fun upsert(entity: CalendarEventCacheEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CalendarEventCacheEntity>)

    @Query("SELECT * FROM calendar_event_cache WHERE startTime >= :afterMs ORDER BY startTime ASC")
    suspend fun getUpcomingEvents(afterMs: Long): List<CalendarEventCacheEntity>

    @Query("SELECT * FROM calendar_event_cache WHERE startTime >= :afterMs ORDER BY startTime ASC LIMIT 1")
    suspend fun getNextEvent(afterMs: Long): CalendarEventCacheEntity?

    @Query("DELETE FROM calendar_event_cache WHERE endTime < :timestampMs")
    suspend fun deleteEventsBefore(timestampMs: Long): Int
}
