package com.contextos.core.service.agent.openclaw

import com.contextos.core.data.model.DraftingContext
import com.contextos.core.data.model.SituationAnalysis
import com.contextos.core.data.model.SituationModel

/**
 * A single turn in a multi-turn conversation with the OpenClaw agent.
 */
data class ChatTurn(
    val role: String,     // "user", "model", or "system"
    val content: String,
)

interface OpenClawAgent {
    /**
     * Sends the current situation model to the OpenClaw agent for structured reasoning.
     * Returns a parsed SituationAnalysis object.
     */
    suspend fun analyzeSituation(model: SituationModel): SituationAnalysis

    /**
     * Sends a drafting context to the OpenClaw LLM to generate a contextually
     * appropriate, natural-sounding message.
     */
    suspend fun draftMessage(context: DraftingContext): String

    /**
     * Multi-turn conversational chat with the OpenClaw agent.
     *
     * The [history] contains the full conversation so far (system prompt + user/model turns).
     * Returns the assistant's response text.
     */
    suspend fun chat(history: List<ChatTurn>): String
}
