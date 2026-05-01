package com.contextos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.contextos.core.data.db.entity.PreferenceMemoryEntity

@Dao
interface PreferenceMemoryDao {

    @Upsert
    suspend fun upsert(entity: PreferenceMemoryEntity)

    @Query("SELECT * FROM preference_memory WHERE skill_id = :skillId AND context_hash = :contextHash LIMIT 1")
    suspend fun getBySkillAndContext(skillId: String, contextHash: String): PreferenceMemoryEntity?

    @Query("SELECT * FROM preference_memory WHERE skill_id = :skillId ORDER BY frequency DESC")
    suspend fun getBySkillId(skillId: String): List<PreferenceMemoryEntity>

    @Query("DELETE FROM preference_memory WHERE skill_id = :skillId")
    suspend fun deleteBySkillId(skillId: String): Int
}
