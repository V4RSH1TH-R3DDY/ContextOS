package com.contextos.core.skills

import android.util.Log
import com.contextos.core.data.model.NudgeMessages
import com.contextos.core.data.model.RoutineType
import com.contextos.core.data.model.SituationModel
import com.contextos.core.data.preferences.PreferencesManager
import com.contextos.core.memory.PersonalRoutineService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proactively nudges the user based on learned routines.
 *
 * Features:
 *   - Queries [PersonalRoutineService] for routines matching the current time slot
 *   - Enforces frequency cap: max 2 nudges/day, min 3 hours apart
 *   - Respects suppressed routine types (user's "Stop suggesting this" preference)
 *   - Non-invasive: notification priority LOW, no sound, no vibration
 *   - Logs all nudges with full ReasoningPayload for transparency
 *
 * Phase 10.2 — Proactive Personal Nudges
 */
@Singleton
class PersonalNudgeSkill @Inject constructor(
    private val personalRoutineService: PersonalRoutineService,
    private val preferencesManager: PreferencesManager,
) : Skill {

    override val id: String = "personal_nudge"
    override val name: String = "Personal Nudge"
    override val description: String = "Proactively nudges you based on learned routines"

    // Track when last nudge was sent to enforce minimum 3-hour gap
    private var lastNudgeTimeMs: Long = 0
    private val MIN_NUDGE_INTERVAL_MS = 3 * 60 * 60 * 1000L // 3 hours
    private var nudgeCountToday = 0
    private val MAX_NUDGES_PER_DAY = 2
    private var lastNudgeDate: Long = 0

    override fun shouldTrigger(model: SituationModel): Boolean {
        // Check nudge frequency cap
        val now = model.currentTime
        val today = now / (24 * 60 * 60 * 1000L) // Simple day calculation

        if (today != lastNudgeDate) {
            nudgeCountToday = 0
            lastNudgeDate = today
        }

        if (nudgeCountToday >= MAX_NUDGES_PER_DAY) {
            return false
        }

        if (now - lastNudgeTimeMs < MIN_NUDGE_INTERVAL_MS) {
            return false
        }

        // For shouldTrigger, we'll return true if it's been more than the minimum interval
        // The actual routine check and suppression filtering happen in execute()
        return true
    }

    override suspend fun execute(model: SituationModel): SkillResult {
        // Get suppressed routine types
        val suppressedTypes = try {
            preferencesManager.suppressedRoutineTypes.first()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read suppressed types", e)
            emptySet()
        }

        // Get suggestions and filter out suppressed types
        val suggestions = personalRoutineService.getSuggestionsForNow(model.currentTime)
            .filter { it.routineType !in suppressedTypes }

        val topSuggestion = suggestions.firstOrNull()
            ?: return SkillResult.Skipped("No active, non-suppressed routines match current time slot")

        // Determine the routine type for enhanced messaging
        val routineType = try {
            RoutineType.valueOf(topSuggestion.routineType)
        } catch (_: Exception) {
            RoutineType.GENERIC
        }

        val nudgeMessage = topSuggestion.suggestedAction.ifBlank {
            NudgeMessages.getMessageForRoutine(routineType)
        }

        nudgeCountToday++
        lastNudgeTimeMs = model.currentTime

        Log.i(TAG, "Nudge triggered: ${routineType.displayName} → $nudgeMessage")

        return SkillResult.Success(
            description = nudgeMessage,
        )
    }

    companion object {
        private const val TAG = "PersonalNudgeSkill"
    }
}
