package com.contextos.core.service.agent

import android.util.Log
import com.contextos.core.data.model.DraftingContext
import com.contextos.core.service.agent.openclaw.OpenClawAgent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates context-appropriate message drafts.
 *
 * Primary path delegates to [OpenClawAgent.draftMessage] for natural-sounding,
 * tone-aware output. If the LLM call fails or returns empty, falls back to
 * deterministic rule-based templates so the user always gets a draft.
 *
 * Phase 3.5 — LLM Message Drafting Foundation
 */
@Singleton
class MessageDraftingEngine @Inject constructor(
    private val openClawAgent: OpenClawAgent,
) {

    /**
     * Drafts a short, natural-sounding message for the given [context].
     *
     * @return A draft string ready to be shown to the user for review.
     *         Never empty — falls back to rule-based templates on any error.
     */
    suspend fun draft(context: DraftingContext): String {
        return try {
            val llmDraft = openClawAgent.draftMessage(context)
            if (llmDraft.isNotBlank()) {
                llmDraft
            } else {
                Log.w(TAG, "OpenClaw returned empty draft — using fallback")
                ruleFallback(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenClaw drafting failed — using fallback", e)
            ruleFallback(context)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule-based fallback (always available offline)
    // ─────────────────────────────────────────────────────────────────────────

    private fun ruleFallback(context: DraftingContext): String {
        return when (context.reason) {
            "Running late" -> {
                val eta = context.estimatedTimeOfArrival ?: "soon"
                "Hey ${context.recipientName}, stuck in traffic — should be there by $eta. Sorry!"
            }
            "Battery warning" -> {
                val time = context.timeAvailable ?: "later"
                val backup = context.backupNumber?.let { " — call $it if urgent" } ?: ""
                "My phone's dying, in meetings until $time$backup"
            }
            "On my way" -> {
                val etaMinutes = context.estimatedTimeOfArrival ?: "a few"
                "Leaving now, ETA is about $etaMinutes minutes"
            }
            else -> {
                "Hey ${context.recipientName}, just wanted to let you know: ${context.reason}."
            }
        }
    }

    companion object {
        private const val TAG = "MessageDraftingEngine"
    }
}
