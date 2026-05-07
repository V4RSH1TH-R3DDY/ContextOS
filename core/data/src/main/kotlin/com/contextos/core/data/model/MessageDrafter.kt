package com.contextos.core.data.model

/**
 * Interface for generating context-appropriate message drafts.
 */
interface MessageDrafter {
    /**
     * Drafts a short, natural-sounding message for the given [context].
     *
     * @return A draft string ready to be shown to the user for review.
     */
    suspend fun draft(context: DraftingContext): String
}
