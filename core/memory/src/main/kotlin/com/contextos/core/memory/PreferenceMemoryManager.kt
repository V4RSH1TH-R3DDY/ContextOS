package com.contextos.core.memory

import com.contextos.core.data.db.dao.PreferenceMemoryDao
import com.contextos.core.data.db.dao.UserPreferenceDao
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
        TODO("Phase 3.3 — PreferenceMemoryManager.recordUserChoice()")
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
        TODO("Phase 3.3 — PreferenceMemoryManager.shouldAutoApprove()")
    }

    /**
     * Removes all preference records for the given skill.
     * Useful when a skill is uninstalled or its behaviour changes significantly.
     */
    suspend fun clearPreferencesForSkill(skillId: String) {
        dao.deleteBySkillId(skillId)
    }
}
