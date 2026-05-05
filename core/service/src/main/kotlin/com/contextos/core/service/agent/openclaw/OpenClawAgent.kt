package com.contextos.core.service.agent.openclaw

import com.contextos.core.data.model.DraftingContext
import com.contextos.core.data.model.SituationAnalysis
import com.contextos.core.data.model.SituationModel

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
}
