package com.contextos.core.skills

import android.app.NotificationManager
import android.content.Context
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.data.model.SituationModel
import com.contextos.core.data.repository.ActionLogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class DndSetterSkillTest {

    private lateinit var skill: DndSetterSkill
    private val mockContext = mockk<Context>()
    private val mockActionLogRepository = mockk<ActionLogRepository>()
    private val mockUserPreferenceDao = mockk<UserPreferenceDao>()
    private val mockNotificationManager = mockk<NotificationManager>()

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        val mockLooper = mockk<Looper>()
        every { Looper.getMainLooper() } returns mockLooper
        
        every { mockContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockNotificationManager
        every { mockNotificationManager.currentInterruptionFilter } returns NotificationManager.INTERRUPTION_FILTER_ALL
        every { mockNotificationManager.isNotificationPolicyAccessGranted } returns true

        skill = DndSetterSkill(mockContext, mockActionLogRepository, mockUserPreferenceDao)
    }

    @Test
    fun `shouldTrigger returns true when meeting starts within 10 minutes and has 2+ attendees`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Team Sync",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(5),
            endTime = now + TimeUnit.MINUTES.toMillis(65),
            attendees = listOf("alice@example.com", "bob@example.com")
        )
        val model = createModel(now, event)

        assertTrue(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when meeting has less than 2 attendees`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Focus Time",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(5),
            endTime = now + TimeUnit.MINUTES.toMillis(65),
            attendees = listOf("alice@example.com")
        )
        val model = createModel(now, event)

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when meeting starts in more than 10 minutes`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Team Sync",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(15),
            endTime = now + TimeUnit.MINUTES.toMillis(75),
            attendees = listOf("alice@example.com", "bob@example.com")
        )
        val model = createModel(now, event)

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when DND is already enabled`() {
        every { mockNotificationManager.currentInterruptionFilter } returns NotificationManager.INTERRUPTION_FILTER_NONE
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Team Sync",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(5),
            endTime = now + TimeUnit.MINUTES.toMillis(65),
            attendees = listOf("alice@example.com", "bob@example.com")
        )
        val model = createModel(now, event)

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when DND permission is missing`() {
        every { mockNotificationManager.isNotificationPolicyAccessGranted } returns false
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Team Sync",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(5),
            endTime = now + TimeUnit.MINUTES.toMillis(65),
            attendees = listOf("alice@example.com", "bob@example.com")
        )
        val model = createModel(now, event)

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when no upcoming event`() {
        val now = System.currentTimeMillis()
        val model = createModel(now, null)

        assertFalse(skill.shouldTrigger(model))
    }

    private fun createModel(now: Long, event: CalendarEventSummary?): SituationModel {
        return SituationModel(
            currentTime = now,
            currentLocation = null,
            batteryLevel = 50,
            isCharging = false,
            nextCalendarEvent = event,
            upcomingCalendarEvents = event?.let { listOf(it) } ?: emptyList()
        )
    }
}
