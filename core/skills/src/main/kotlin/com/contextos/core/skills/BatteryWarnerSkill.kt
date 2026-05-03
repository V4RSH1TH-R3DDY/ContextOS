package com.contextos.core.skills

import android.content.Context
import android.content.SharedPreferences
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.model.ActionOutcome
import com.contextos.core.data.model.SituationModel
import com.contextos.core.data.repository.ActionLogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "contextos_preferences"
private const val KEY_EMERGENCY_CONTACT_NAME = "emergency_contact_name"
private const val KEY_EMERGENCY_CONTACT_PHONE = "emergency_contact_phone"

/**
 * Battery Warner skill — warns an emergency contact when battery is critically low
 * and the user has a long meeting ahead.
 *
 * Trigger conditions:
 *  - Battery below 20% AND not charging
 *  - Next calendar event is longer than 90 minutes OR the next 3 hours have 2+ events
 *  - Has not triggered successfully in the last 2 hours (debounce)
 *
 * Execution:
 *  - Composes a pre-drafted SMS message
 *  - If auto-approve is enabled, sends SMS directly via SmsManager
 *  - Otherwise returns PendingConfirmation with the message for user approval
 */
@Singleton
class BatteryWarnerSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actionLogRepository: ActionLogRepository,
    private val userPreferenceDao: UserPreferenceDao,
) : Skill {

    override val id: String = "battery_warner"
    override val name: String = "Battery Warner"
    override val description: String = "Warns your emergency contact when battery is low before a long meeting."

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun shouldTrigger(model: SituationModel): Boolean {
        if (model.batteryLevel >= BATTERY_THRESHOLD) return false
        if (model.isCharging) return false

        val nextEvent = model.nextCalendarEvent ?: return false

        val eventDurationMin = TimeUnit.MILLISECONDS.toMinutes(nextEvent.endTime - nextEvent.startTime)
        val hasLongMeeting = eventDurationMin >= LONG_MEETING_MINUTES

        val eventsIn3Hours = model.upcomingCalendarEvents.count { event ->
            val timeUntilEvent = TimeUnit.MILLISECONDS.toMinutes(event.startTime - model.currentTime)
            timeUntilEvent in 0..180
        }
        val hasBusySchedule = eventsIn3Hours >= BUSY_EVENTS_THRESHOLD

        if (!hasLongMeeting && !hasBusySchedule) return false

        return true
    }

    override suspend fun execute(model: SituationModel): SkillResult = withContext(Dispatchers.Default) {
        val contactName = prefs.getString(KEY_EMERGENCY_CONTACT_NAME, null)
        val contactPhone = prefs.getString(KEY_EMERGENCY_CONTACT_PHONE, null)

        if (contactName.isNullOrEmpty() || contactPhone.isNullOrEmpty()) {
            return@withContext SkillResult.Failure(
                description = "No emergency contact configured — open Settings to set one up",
                outcome = ActionOutcome.FAILURE,
            )
        }

        val triggeredInLast2Hours = actionLogRepository.countSuccessfulTriggersSince(
            skillId = id,
            sinceMs = model.currentTime - DEBOUNCE_WINDOW_MS,
        )
        if (triggeredInLast2Hours > 0) {
            return@withContext SkillResult.Skipped("Already warned contact within the last 2 hours")
        }

        val nextEvent = model.nextCalendarEvent ?: return@withContext SkillResult.Skipped("No calendar event found")
        val eventDurationHours = String.format("%.1f",
            TimeUnit.MILLISECONDS.toMinutes(nextEvent.endTime - nextEvent.startTime) / 60.0)

        val message = "Hi $contactName, my phone battery is at ${model.batteryLevel}%. " +
            "I have meetings for the next $eventDurationHours hours — might be unreachable if it dies."

        val userPref = userPreferenceDao.getBySkillId(id)
        val shouldAutoApprove = userPref?.autoApprove ?: false

        return@withContext if (shouldAutoApprove) {
            trySendSms(contactPhone, message, contactName, model.batteryLevel)
        } else {
            SkillResult.PendingConfirmation(
                description = "Send low-battery warning SMS to $contactName",
                confirmationMessage = message,
                pendingAction = {
                    trySendSms(contactPhone, message, contactName, model.batteryLevel)
                },
            )
        }
    }

    private fun trySendSms(phone: String, message: String, contactName: String, batteryLevel: Int): SkillResult {
        return try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(phone, null, message, null, null)

            SkillResult.Success(
                description = "Sent battery warning SMS to $contactName ($batteryLevel%)",
                outcome = ActionOutcome.SUCCESS,
            )
        } catch (e: SecurityException) {
            SkillResult.Failure(
                description = "SMS permission not granted",
                error = e,
                outcome = ActionOutcome.FAILURE,
            )
        } catch (e: Exception) {
            SkillResult.Failure(
                description = "Failed to send SMS: ${e.message}",
                error = e,
                outcome = ActionOutcome.FAILURE,
            )
        }
    }

    companion object {
        private const val BATTERY_THRESHOLD = 20
        private const val LONG_MEETING_MINUTES = 90L
        private const val BUSY_EVENTS_THRESHOLD = 2
        private const val DEBOUNCE_WINDOW_MS = 2 * 60 * 60 * 1000L // 2 hours
    }
}
