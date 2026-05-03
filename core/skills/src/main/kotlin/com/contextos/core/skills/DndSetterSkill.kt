package com.contextos.core.skills

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.model.ActionOutcome
import com.contextos.core.data.model.SituationModel
import com.contextos.core.data.repository.ActionLogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DND Setter skill — automatically enables Do Not Disturb before meetings.
 *
 * Trigger conditions:
 *  - Next calendar event starts within 10 minutes
 *  - Event has 2+ attendees (not a personal reminder)
 *  - DND is not already enabled
 *  - User hasn't dismissed this skill more than twice in the last 7 days
 *
 * Execution:
 *  - Enables DND via NotificationManager.setInterruptionFilter
 *  - Schedules automatic re-enable after meeting ends + 5 minute buffer
 *  - Logs action for the action log feed
 */
@Singleton
class DndSetterSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actionLogRepository: ActionLogRepository,
    private val userPreferenceDao: UserPreferenceDao,
) : Skill {

    override val id: String = "dnd_setter"
    override val name: String = "DND Setter"
    override val description: String = "Auto-enables Do Not Disturb before meetings with 2+ attendees."

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun shouldTrigger(model: SituationModel): Boolean {
        val event = model.nextCalendarEvent ?: return false

        if (event.attendees.size < 2) return false

        val nowMs = model.currentTime
        val minutesUntilStart = TimeUnit.MILLISECONDS.toMinutes(event.startTime - nowMs)
        if (minutesUntilStart !in 0..10) return false

        if (notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
            return false
        }

        if (!hasDndAccess()) return false

        return true
    }

    override suspend fun execute(model: SituationModel): SkillResult {
        val event = model.nextCalendarEvent ?: return SkillResult.Skipped("No calendar event found")

        val nowMs = model.currentTime
        val minutesUntilStart = TimeUnit.MILLISECONDS.toMinutes(event.startTime - nowMs)
        if (minutesUntilStart < 0) {
            return SkillResult.Skipped("Event already started")
        }

        val dismissalsInLast7Days = actionLogRepository.countDismissalsSince(
            skillId = id,
            sinceMs = model.currentTime - DISMISSAL_WINDOW_MS,
        )
        if (dismissalsInLast7Days > DISMISSAL_THRESHOLD) {
            return SkillResult.Skipped("User has dismissed this skill multiple times recently — respecting preference")
        }

        return try {
            val currentFilter = notificationManager.currentInterruptionFilter
            if (currentFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                return SkillResult.Skipped("DND is already enabled")
            }

            enableDnd()
            scheduleDisableDnd(event.endTime)

            val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
            val endTimeStr = formatter.format(Date(event.endTime))

            SkillResult.Success(
                description = "Enabled DND until $endTimeStr before '${event.title}'",
                outcome = ActionOutcome.SUCCESS,
            )
        } catch (e: SecurityException) {
            SkillResult.Failure(
                description = "DND permission not granted — cannot enable Do Not Disturb",
                error = e,
                outcome = ActionOutcome.FAILURE,
            )
        } catch (e: Exception) {
            SkillResult.Failure(
                description = "Failed to enable DND: ${e.message}",
                error = e,
                outcome = ActionOutcome.FAILURE,
            )
        }
    }

    private fun hasDndAccess(): Boolean {
        return notificationManager.isNotificationPolicyAccessGranted
    }

    private fun enableDnd() {
        if (!hasDndAccess()) {
            throw SecurityException("Notification policy access not granted")
        }
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
    }

    private fun scheduleDisableDnd(eventEndTimeMs: Long) {
        val delayMs = eventEndTimeMs + DND_RE_ENABLE_BUFFER_MS - System.currentTimeMillis()
        if (delayMs > 0) {
            handler.postDelayed({
                try {
                    if (hasDndAccess()) {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to disable DND: ${e.message}")
                }
            }, delayMs)
        }
    }

    companion object {
        private const val TAG = "DndSetterSkill"
        private const val DND_RE_ENABLE_BUFFER_MS = 5 * 60 * 1000L // 5 minutes
        private const val DISMISSAL_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
        private const val DISMISSAL_THRESHOLD = 2
    }
}
