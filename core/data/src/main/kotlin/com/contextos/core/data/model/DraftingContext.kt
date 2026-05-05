package com.contextos.core.data.model

data class DraftingContext(
    val recipientName: String,
    val relationship: String, // e.g., "colleague", "friend", "family"
    val reason: String,       // e.g., "Running late", "Battery warning", "On my way"
    val estimatedTimeOfArrival: String? = null,
    val timeAvailable: String? = null,
    val backupNumber: String? = null
)
