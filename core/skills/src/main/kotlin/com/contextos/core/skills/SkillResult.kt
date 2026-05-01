package com.contextos.core.skills

import com.contextos.core.data.model.ActionOutcome

/**
 * Result returned by [Skill.execute].
 */
sealed class SkillResult {
    /** Skill executed successfully. */
    data class Success(
        val description: String,
        val outcome: ActionOutcome = ActionOutcome.SUCCESS,
    ) : SkillResult()

    /** Skill needs user confirmation before executing. */
    data class PendingConfirmation(
        val description: String,
        val confirmationMessage: String,
        val pendingAction: suspend () -> SkillResult,
    ) : SkillResult()

    /** Skill could not execute due to an error. */
    data class Failure(
        val description: String,
        val error: Throwable? = null,
        val outcome: ActionOutcome = ActionOutcome.FAILURE,
    ) : SkillResult()

    /** Skill decided not to run after all (e.g., condition changed). */
    data class Skipped(val reason: String) : SkillResult()
}
