package com.contextos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contextos.core.data.db.entity.ActionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActionLogEntity): Long

    @Query("SELECT * FROM action_log ORDER BY timestampMs DESC")
    fun getAll(): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_log ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_log WHERE skillId = :skillId ORDER BY timestampMs DESC")
    fun getBySkillId(skillId: String): Flow<List<ActionLogEntity>>

    @Query("DELETE FROM action_log WHERE timestampMs < :timestampMs")
    suspend fun deleteOlderThan(timestampMs: Long): Int
}
