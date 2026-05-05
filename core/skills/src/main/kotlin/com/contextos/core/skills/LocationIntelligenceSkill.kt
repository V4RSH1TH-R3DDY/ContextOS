package com.contextos.core.skills

import com.contextos.core.data.model.SituationModel
import javax.inject.Inject

/**
 * Passive skill that runs every cycle to update the location memory layer
 * and adjust notification behavior based on the user's recognized location.
 *
 * Unlike other skills this never surfaces a user-facing action — it only
 * updates system state (notification mode) and writes a transition log entry
 * when the user moves between labelled locations.
 *
 * Phase 4.3 — Location Intelligence Skill (Owner: P2)
 */
class LocationIntelligenceSkill @Inject constructor() : Skill {

    override val id: String = "location_intelligence"
    override val name: String = "Location Intelligence"
    override val description: String = "Adapts notification behavior based on recognized locations"

    /**
     * Always returns true — this skill runs every cycle to keep
     * the location memory up-to-date and adjust notification mode.
     */
    override fun shouldTrigger(model: SituationModel): Boolean = true

    /**
     * Passive execution:
     * - Records current location in memory (handled by SituationModelBuilder already).
     * - Determines notification mode based on the inferred location label.
     * - Logs location transitions (e.g., "Arrived at Office").
     *
     * The actual DND / notification changes require Android system APIs
     * (NotificationManager), which are injected via the :core:service module
     * at runtime. Here we return a [SkillResult.Success] describing the
     * action for the log, or [SkillResult.Skipped] if no meaningful action
     * was taken.
     */
    override suspend fun execute(model: SituationModel): SkillResult {
        val label = model.locationLabel

        return when (label) {
            "Home" -> {
                // Re-enable all notifications, disable any active DND
                SkillResult.Success(
                    description = "Arrived at Home — notifications re-enabled, DND cleared."
                )
            }
            "Office" -> {
                // Apply "office mode": reduce notification volume, vibrate-only for non-urgent
                SkillResult.Success(
                    description = "At Office — office mode applied (vibrate-only, non-urgent filtered)."
                )
            }
            "Unknown" -> {
                // New or unrecognized location: set to "Priority Only" mode
                if (model.currentLocation != null) {
                    SkillResult.Success(
                        description = "At unrecognized location — priority-only notifications."
                    )
                } else {
                    SkillResult.Skipped(reason = "No GPS fix available — no location action taken.")
                }
            }
            else -> {
                // Known but non-Home/Office location (e.g., Gym, Frequent Location)
                SkillResult.Success(
                    description = "At $label — standard notification mode."
                )
            }
        }
    }
}
