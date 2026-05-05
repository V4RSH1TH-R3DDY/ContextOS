package com.contextos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contextos.core.data.db.entity.ConfirmedRoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfirmedRoutineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConfirmedRoutineEntity): Long

    @Query("SELECT * FROM confirmed_routine WHERE is_active = 1 ORDER BY day_of_week, time_slot_start")
    fun getAllActive(): Flow<List<ConfirmedRoutineEntity>>

    @Query("SELECT * FROM confirmed_routine WHERE day_of_week = :dayOfWeek AND time_slot_start <= :timeMinutes AND time_slot_end >= :timeMinutes AND is_active = 1")
    suspend fun getForTimeSlot(dayOfWeek: Int, timeMinutes: Int): List<ConfirmedRoutineEntity>

    @Query("UPDATE confirmed_routine SET is_active = :isActive WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean)

    @Query("DELETE FROM confirmed_routine WHERE id = :id")
    suspend fun delete(id: Long)
}
