package com.contextos.core.skills

import com.contextos.core.data.model.SituationModel
import com.contextos.core.memory.PersonalRoutineService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonalNudgeSkill @Inject constructor(
    private val personalRoutineService: PersonalRoutineService,
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
        // The actual routine check will happen in execute()
        return true
    }
    
    override suspend fun execute(model: SituationModel): SkillResult {
        val suggestions = personalRoutineService.getSuggestionsForNow(model.currentTime)
        val topSuggestion = suggestions.firstOrNull() ?: return SkillResult.Skipped("No routines match current time slot")
        
        nudgeCountToday++
        lastNudgeTimeMs = model.currentTime
        
        return SkillResult.Success(
            description = topSuggestion.suggestedAction,
        )
    }
}
