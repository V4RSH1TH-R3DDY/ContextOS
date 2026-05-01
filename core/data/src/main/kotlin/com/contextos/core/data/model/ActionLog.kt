package com.contextos.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ActionLogEntry(
    val id: Long = 0,
    val timestampMs: Long,
    val skillId: String,
    val skillName: String,
    val description: String,
    val wasAutoApproved: Boolean,
    val userOverride: UserOverride?,
    val situationSnapshot: String, // JSON-serialized SituationModel
    val outcome: ActionOutcome,
)

@Serializable
enum class UserOverride { APPROVED, DISMISSED, MODIFIED }

@Serializable
enum class ActionOutcome { SUCCESS, FAILURE, PENDING_USER_CONFIRMATION, SKIPPED }
