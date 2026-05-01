package com.contextos.core.skills

import com.contextos.core.data.model.SituationModel

/**
 * Base interface that every ContextOS skill must implement.
 * Skills are stateless — all state lives in the database or is passed via [SituationModel].
 */
interface Skill {
    /** Unique stable identifier used in DB, logs, and user preferences. */
    val id: String

    /** Human-readable name shown in the UI. */
    val name: String

    /** Human-readable description of what this skill does. */
    val description: String

    /**
     * Pure (non-suspending) function checked by the agent on every cycle.
     * Must be fast — no I/O allowed here.
     * @return true if this skill wants to run given the current situation.
     */
    fun shouldTrigger(model: SituationModel): Boolean

    /**
     * Execute the skill's action. Called only when [shouldTrigger] returns true
     * and the action dispatcher approves execution.
     */
    suspend fun execute(model: SituationModel): SkillResult
}
