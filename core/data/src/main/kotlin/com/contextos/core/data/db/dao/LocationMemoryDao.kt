package com.contextos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.contextos.core.data.db.entity.LocationMemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationMemoryDao {

    @Upsert
    suspend fun upsert(entity: LocationMemoryEntity)

    @Query("SELECT * FROM location_memory WHERE latLngHash = :latLngHash LIMIT 1")
    suspend fun getByHash(latLngHash: String): LocationMemoryEntity?

    @Query("SELECT * FROM location_memory WHERE inferredLabel != 'Unknown' ORDER BY visitCount DESC")
    suspend fun getLabelledLocations(): List<LocationMemoryEntity>

    @Query("SELECT * FROM location_memory ORDER BY lastVisitedMs DESC")
    fun getAll(): Flow<List<LocationMemoryEntity>>

    @Query("DELETE FROM location_memory WHERE latLngHash = :latLngHash")
    suspend fun deleteByHash(latLngHash: String): Int
}
