package com.contextos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.contextos.core.data.db.entity.UserPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferenceDao {

    @Upsert
    suspend fun upsert(entity: UserPreferenceEntity)

    @Query("SELECT * FROM user_preferences WHERE skill_id = :skillId LIMIT 1")
    suspend fun getBySkillId(skillId: String): UserPreferenceEntity?

    @Query("SELECT * FROM user_preferences ORDER BY skill_id ASC")
    fun getAll(): Flow<List<UserPreferenceEntity>>

    @Query("DELETE FROM user_preferences WHERE skill_id = :skillId")
    suspend fun deleteBySkillId(skillId: String): Int
}
