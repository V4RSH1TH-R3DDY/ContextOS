package com.contextos.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ReasoningPayload(
    val contextLabel: String = "",
    val confidenceScore: Float = 0.0f,
    val reasoningPoints: List<String> = emptyList(),
    val anomalyFlags: List<String> = emptyList(),
    val dataSourcesUsed: List<String> = emptyList(),
)
