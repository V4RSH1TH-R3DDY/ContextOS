package com.contextos.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SituationAnalysis(
    val currentContextLabel: String,
    val urgencyLevel: Int,
    val recommendedSkills: List<ActionRecommendation>,
    val anomalyFlags: List<String>
)
