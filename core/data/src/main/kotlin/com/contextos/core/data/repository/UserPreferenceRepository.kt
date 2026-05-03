package com.contextos.core.data.repository

import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.db.entity.UserPreferenceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferenceRepository @Inject constructor(
    private val dao: UserPreferenceDao,
) {

    suspend fun upsert(skillId: String, autoApprove: Boolean, sensitivityLevel: Int) {
        dao.upsert(
            UserPreferenceEntity(
                skillId = skillId,
                autoApprove = autoApprove,
                sensitivityLevel = sensitivityLevel,
            )
        )
    }

    suspend fun getBySkillId(skillId: String): UserPreferenceEntity? =
        dao.getBySkillId(skillId)

    fun getAll(): Flow<List<UserPreferenceEntity>> = dao.getAll()

    suspend fun deleteBySkillId(skillId: String) = dao.deleteBySkillId(skillId)
}
