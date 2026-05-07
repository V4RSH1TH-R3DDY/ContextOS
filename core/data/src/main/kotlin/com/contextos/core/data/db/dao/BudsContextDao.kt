package com.contextos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contextos.core.data.db.entity.BudsContextEntity

/**
 * DAO for Galaxy Buds audio context data.
 *
 * Phase 11.2 — Galaxy Buds Integration
 */
@Dao
interface BudsContextDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BudsContextEntity): Long

    @Query("SELECT * FROM buds_context ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): BudsContextEntity?

    @Query("SELECT * FROM buds_context WHERE timestamp > :sinceMs ORDER BY timestamp DESC")
    suspend fun getSince(sinceMs: Long): List<BudsContextEntity>

    @Query("DELETE FROM buds_context WHERE timestamp < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int
}
