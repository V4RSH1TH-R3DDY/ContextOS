package com.contextos.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_log")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val skillId: String,
    val skillName: String,
    val description: String,
    val wasAutoApproved: Boolean,
    val userOverride: String?,      // nullable, stores UserOverride.name()
    val situationSnapshot: String,  // JSON
    val reasoningPayload: String,   // JSON for ReasoningPayload
    val outcome: String,            // ActionOutcome.name()
)
