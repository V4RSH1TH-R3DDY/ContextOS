package com.contextos.core.memory

import com.contextos.core.data.db.dao.PreferenceMemoryDao
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.db.entity.PreferenceMemoryEntity
import com.contextos.core.data.db.entity.UserPreferenceEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the user-preference learning subsystem.
 *
 * Tracks per-skill, per-context-hash user choices (APPROVED / DISMISSED) and
 * promotes frequently confirmed actions to auto-approval once a confidence
 * threshold is reached.
 *
 * Full implementation: Phase 3.3 — Preference Memory System
 */
@Singleton
class PreferenceMemoryManager @Inject constructor(
    private val dao: PreferenceMemoryDao,
    private val userPrefDao: UserPreferenceDao,
) {
    /**
     * Persist a user's choice for a particular skill + context combination.
     *
     * @param skillId     Stable skill identifier (e.g., "dnd_setter").
     * @param contextHash SHA-256 hash of the relevant SituationModel fields
     *                    used by this skill to decide whether to trigger.
     * @param choice      "APPROVED" or "DISMISSED".
     */
    suspend fun recordUserChoice(skillId: String, contextHash: String, choice: String) {
        val existing = dao.getBySkillAndContext(skillId, contextHash)
        val now = System.currentTimeMillis()
        if (existing != null) {
            val isSameChoice = existing.userChoice == choice
            val updated = if (isSameChoice) {
                existing.copy(frequency = existing.frequency + 1, lastObservedMs = now)
            } else {
                existing.copy(userChoice = choice, frequency = 1, lastObservedMs = now)
            }
            dao.upsert(updated)
            
            // Check if we should update autoApprove (3+ consistent approvals)
            if (updated.userChoice == "APPROVED" && updated.frequency >= 3) {
                val pref = userPrefDao.getBySkillId(skillId)
                if (pref != null) {
                    if (!pref.autoApprove) {
                        userPrefDao.upsert(pref.copy(autoApprove = true))
                    }
                } else {
                    userPrefDao.upsert(UserPreferenceEntity(skillId = skillId, autoApprove = true))
                }
            }
        } else {
            dao.upsert(PreferenceMemoryEntity(
                skillId = skillId,
                contextHash = contextHash,
                userChoice = choice,
                frequency = 1,
                lastObservedMs = now
            ))
        }
    }

    /**
     * Returns true if the given skill should auto-approve for this context.
     *
     * Decision is based on historical frequency and the [UserPreferenceEntity.autoApprove]
     * flag persisted in [userPrefDao].
     *
     * @param skillId     Stable skill identifier.
     * @param contextHash SHA-256 hash of the relevant SituationModel fields.
     */
    suspend fun shouldAutoApprove(skillId: String, contextHash: String): Boolean {
        val userPref = userPrefDao.getBySkillId(skillId)
        if (userPref != null && userPref.autoApprove) {
            return true
        }
        val memory = dao.getBySkillAndContext(skillId, contextHash)
        return memory != null && memory.userChoice == "APPROVED" && memory.frequency >= 3
    }

    /**
     * Removes all preference records for the given skill.
     * Useful when a skill is uninstalled or its behaviour changes significantly.
     */
    suspend fun clearPreferencesForSkill(skillId: String) {
        dao.deleteBySkillId(skillId)
    }
}