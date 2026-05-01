package com.contextos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.contextos.core.data.db.entity.RoutineMemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineMemoryDao {

    @Upsert
    suspend fun upsert(entity: RoutineMemoryEntity)

    @Query("SELECT * FROM routine_memory WHERE day_of_week = :dayOfWeek AND time_slot = :timeSlot LIMIT 1")
    suspend fun getByDayAndSlot(dayOfWeek: Int, timeSlot: String): RoutineMemoryEntity?

    @Query("SELECT * FROM routine_memory WHERE confidence >= :minConfidence ORDER BY confidence DESC")
    suspend fun getAllHighConfidence(minConfidence: Float): List<RoutineMemoryEntity>

    @Query("SELECT * FROM routine_memory ORDER BY day_of_week ASC, time_slot ASC")
    fun getAll(): Flow<List<RoutineMemoryEntity>>
}
