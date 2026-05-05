package com.contextos.core.service.agent

import com.contextos.core.data.model.SituationModel
import com.contextos.core.data.model.SituationAnalysis
import com.contextos.core.service.agent.openclaw.OpenClawAgent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SituationModeler @Inject constructor(
    private val openClawAgent: OpenClawAgent
) {
    
    suspend fun analyze(model: SituationModel): SituationAnalysis {
        // Delegate structured reasoning to the OpenClaw agent
        return openClawAgent.analyzeSituation(model)
    }
}
