package com.contextos.core.service.agent

import android.util.Log
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.db.entity.ActionLogEntity
import com.contextos.core.data.model.ActionOutcome
import com.contextos.core.data.model.ReasoningPayload
import com.contextos.core.data.model.SituationModel
import com.contextos.core.data.repository.ActionLogRepository
import com.contextos.core.service.ReasoningBuilder
import com.contextos.core.service.notifications.ContextOSNotificationManager
import com.contextos.core.skills.Skill
import com.contextos.core.skills.SkillResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes approved [SkillResult] objects to execution and persists every decision
 * to the [ActionLogRepository].
 *
 * Dispatch rules:
 * - If `UserPreferenceEntity.autoApprove == true` (or no preference set), execute immediately.
 * - If `autoApprove == false`, [SkillResult.PendingConfirmation] is stored for the user to act on.
 * - All outcomes (success, failure, skipped, pending) are written to the action log.
 */
@Singleton
class ActionDispatcher @Inject constructor(
    private val userPreferenceDao:  UserPreferenceDao,
    private val actionLogRepository: ActionLogRepository,
    private val notificationManager: ContextOSNotificationManager,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val reasoningBuilder = ReasoningBuilder()

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles the [result] produced by [skill.execute] and persists an [ActionLogEntity].
     *
     * For [SkillResult.PendingConfirmation] results the pending action is NOT executed here —
     * the user sees the log entry in the Dashboard and taps Approve/Dismiss (Phase 5).
     */
    suspend fun dispatch(
        skill:  Skill,
        result: SkillResult,
        model:  SituationModel,
        confidence: Float = 0.85f,
    ) {
        val prefs          = userPreferenceDao.getBySkillId(skill.id)
        val autoApprove    = prefs?.autoApprove ?: false
        val situationJson  = serializeSituation(model)
        val reasoningJson  = serializeReasoning(reasoningBuilder.buildReasoningPayload(model, skill.id, confidence))
        val nowMs          = System.currentTimeMillis()

        when (result) {
            is SkillResult.Success -> {
                Log.i(TAG, "[${skill.id}] Success — ${result.description}")
                log(
                    now              = nowMs,
                    skill            = skill,
                    description      = result.description,
                    outcome          = ActionOutcome.SUCCESS,
                    wasAutoApproved  = true,
                    situationJson    = situationJson,
                    reasoningJson    = reasoningJson,
                )
            }

            is SkillResult.PendingConfirmation -> {
                if (autoApprove) {
                    // Execute immediately on behalf of the user
                    Log.i(TAG, "[${skill.id}] Auto-approving pending action")
                    val innerResult = try {
                        result.pendingAction()
                    } catch (e: Exception) {
                        Log.e(TAG, "[${skill.id}] Auto-approved action failed", e)
                        SkillResult.Failure("Auto-approval failed: ${e.message}", e)
                    }
                    dispatch(skill, innerResult, model, confidence)  // recurse with the real result
                } else {
                    Log.i(TAG, "[${skill.id}] Pending user confirmation — ${result.confirmationMessage}")
                    val actionLogId = log(
                        now              = nowMs,
                        skill            = skill,
                        description      = result.confirmationMessage,
                        outcome          = ActionOutcome.PENDING_USER_CONFIRMATION,
                        wasAutoApproved  = false,
                        situationJson    = situationJson,
                        reasoningJson    = reasoningJson,
                    )
                    showNotification(skill.id, result, actionLogId)
                }
            }

            is SkillResult.Failure -> {
                Log.e(TAG, "[${skill.id}] Failure — ${result.description}", result.error)
                log(
                    now              = nowMs,
                    skill            = skill,
                    description      = result.description,
                    outcome          = ActionOutcome.FAILURE,
                    wasAutoApproved  = true,
                    situationJson    = situationJson,
                    reasoningJson    = reasoningJson,
                )
            }

            is SkillResult.Skipped -> {
                Log.d(TAG, "[${skill.id}] Skipped — ${result.reason}")
                log(
                    now              = nowMs,
                    skill            = skill,
                    description      = result.reason,
                    outcome          = ActionOutcome.SKIPPED,
                    wasAutoApproved  = true,
                    situationJson    = situationJson,
                    reasoningJson    = reasoningJson,
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun log(
        now:             Long,
        skill:           Skill,
        description:     String,
        outcome:         ActionOutcome,
        wasAutoApproved: Boolean,
        situationJson:   String,
        reasoningJson:   String = "{}",
    ): Long {
        return try {
            actionLogRepository.insert(
                ActionLogEntity(
                    timestampMs       = now,
                    skillId           = skill.id,
                    skillName         = skill.name,
                    description       = description,
                    wasAutoApproved   = wasAutoApproved,
                    userOverride      = null,
                    situationSnapshot = situationJson,
                    reasoningPayload  = reasoningJson,
                    outcome           = outcome.name,
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist ActionLogEntity for ${skill.id}", e)
            -1L
        }
    }

    private fun serializeSituation(model: SituationModel): String {
        return try {
            json.encodeToString(model)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to serialize SituationModel", e)
            "{}"
        }
    }

    private fun serializeReasoning(payload: ReasoningPayload): String {
        return try {
            json.encodeToString(payload)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to serialize ReasoningPayload", e)
            "{}"
        }
    }

    private fun showNotification(skillId: String, result: SkillResult.PendingConfirmation, actionLogId: Long) {
        if (actionLogId < 0L) return

        val title = when (skillId) {
            "battery_warner" -> "Low Battery"
            "message_drafter" -> "Message Draft"
            "navigation_launcher" -> "Navigation"
            else -> "ContextOS Action"
        }

        val extras = result.notificationExtras

        when (skillId) {
            "message_drafter" -> {
                notificationManager.showMessageDraftNotification(
                    notificationId = NOTIFICATION_ID_BASE + (actionLogId % NOTIFICATION_ID_BASE).toInt(),
                    title = title,
                    body = result.confirmationMessage,
                    draftText = result.confirmationMessage,
                    skillId = skillId,
                    actionLogId = actionLogId,
                    extras = extras,
                )
            }
            "battery_warner" -> {
                notificationManager.showBatteryWarningNotification(
                    notificationId = NOTIFICATION_ID_BASE + (actionLogId % NOTIFICATION_ID_BASE).toInt(),
                    title = title,
                    body = result.confirmationMessage,
                    skillId = skillId,
                    actionLogId = actionLogId,
                    extras = extras,
                )
            }
            else -> {
                notificationManager.showApprovalNotification(
                    notificationId = NOTIFICATION_ID_BASE + (actionLogId % NOTIFICATION_ID_BASE).toInt(),
                    title = title,
                    body = result.confirmationMessage,
                    skillId = skillId,
                    actionLogId = actionLogId,
                    extras = extras,
                )
            }
        }
    }

    companion object {
        private const val TAG = "ActionDispatcher"
        private const val NOTIFICATION_ID_BASE = 10_000
    }
}
