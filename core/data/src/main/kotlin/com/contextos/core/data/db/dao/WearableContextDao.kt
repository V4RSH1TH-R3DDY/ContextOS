package com.contextos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contextos.core.data.db.entity.WearableContextEntity

/**
 * DAO for Galaxy Watch wearable context data.
 *
 * Phase 11.1 — Galaxy Watch Integration
 */
@Dao
interface WearableContextDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WearableContextEntity): Long

    @Query("SELECT * FROM wearable_context ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): WearableContextEntity?

    @Query("SELECT * FROM wearable_context WHERE timestamp > :sinceMs ORDER BY timestamp DESC")
    suspend fun getSince(sinceMs: Long): List<WearableContextEntity>

    @Query("DELETE FROM wearable_context WHERE timestamp < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int
}
