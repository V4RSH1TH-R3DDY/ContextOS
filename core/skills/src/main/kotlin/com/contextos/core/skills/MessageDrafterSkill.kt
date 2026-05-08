package com.contextos.core.skills

import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.model.ActionOutcome
import com.contextos.core.data.model.DraftingContext
import com.contextos.core.data.model.SituationModel
import com.contextos.core.data.repository.ActionLogRepository
import com.contextos.core.network.MapsDistanceMatrixClient
import com.contextos.core.data.model.MessageDrafter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageDrafterSkill @Inject constructor(
    private val actionLogRepository: ActionLogRepository,
    private val userPreferenceDao: UserPreferenceDao,
    private val messageDrafter: MessageDrafter,
    private val mapsClient: MapsDistanceMatrixClient
) : Skill {

    override val id: String = "message_drafter"
    override val name: String = "Message Drafter"
    override val description: String = "Drafts context-aware messages when running late or commuting."

    override fun shouldTrigger(model: SituationModel): Boolean {
        val event = model.nextCalendarEvent
        val nowMs = model.currentTime

        if (event != null && event.location?.isNotBlank() == true) {
            val minutesUntilStart = TimeUnit.MILLISECONDS.toMinutes(event.startTime - nowMs)
            if (minutesUntilStart in 0..15) {
                return true
            }
        }

        return false
    }

    override suspend fun execute(model: SituationModel): SkillResult = withContext(Dispatchers.Default) {
        val event = model.nextCalendarEvent ?: return@withContext SkillResult.Skipped("No upcoming event")
        
        val nowMs = model.currentTime
        val minutesUntilStart = TimeUnit.MILLISECONDS.toMinutes(event.startTime - nowMs)
        
        var travelTimeMinutes: Long = 0
        val currentLoc = model.currentLocation
        if (currentLoc != null && event.location?.isNotBlank() == true) {
            val travelTimeResult = mapsClient.getEstimatedTravelTime(
                currentLoc.latitude,
                currentLoc.longitude,
                event.location!!,
                nowMs
            )
            travelTimeMinutes = (travelTimeResult?.durationSeconds ?: 0) / 60
        } else {
            // fallback if no location
            travelTimeMinutes = 20
        }

        if (travelTimeMinutes <= minutesUntilStart) {
            return@withContext SkillResult.Skipped("Likely to arrive on time")
        }

        val recipientName = event.attendees.firstOrNull() ?: "Organizer"
        
        val draftContext = DraftingContext(
            recipientName = recipientName,
            relationship = "colleague",
            reason = "Running late",
            estimatedTimeOfArrival = "$travelTimeMinutes"
        )
        
        val draft = messageDrafter.draft(draftContext)

        val userPref = userPreferenceDao.getBySkillId(id)
        val shouldAutoApprove = userPref?.autoApprove ?: false

        return@withContext if (shouldAutoApprove) {
            recordSuccess(recipientName)
        } else {
            SkillResult.PendingConfirmation(
                description = "Drafted running late message for $recipientName",
                confirmationMessage = draft,
                notificationExtras = mapOf(
                    "message_text" to draft,
                    "contact_name" to recipientName,
                    "recipient_name" to recipientName,
                ),
                pendingAction = {
                    recordSuccess(recipientName)
                }
            )
        }
    }

    private fun recordSuccess(contactName: String): SkillResult {
        return SkillResult.Success(
            description = "Message draft delivered for $contactName",
            outcome = ActionOutcome.SUCCESS
        )
    }
}
