package com.contextos.core.skills

import com.contextos.core.data.model.SituationModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub skill used by the Phase 1 agent bootstrap to prove the full loop writes
 * action-log entries before real Phase 2 skills are installed.
 */
@Singleton
class PhaseOneHeartbeatSkill @Inject constructor() : Skill {
    override val id: String = "system.phase_one_heartbeat"
    override val name: String = "Phase 1 Heartbeat"
    override val description: String = "Records that the ContextOS agent loop completed."

    override fun shouldTrigger(model: SituationModel): Boolean = true

    override suspend fun execute(model: SituationModel): SkillResult =
        SkillResult.Success(
            description = "Agent loop completed with battery=${model.batteryLevel}%",
        )
}
