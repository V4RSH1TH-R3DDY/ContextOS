package com.contextos.core.skills

import android.content.Context
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.data.model.SituationModel
import com.contextos.core.data.repository.ActionLogRepository
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class BatteryWarnerSkillTest {

    private lateinit var skill: BatteryWarnerSkill
    private val mockContext = mockk<Context>()
    private val mockActionLogRepository = mockk<ActionLogRepository>()
    private val mockUserPreferenceDao = mockk<UserPreferenceDao>()

    @Before
    fun setup() {
        skill = BatteryWarnerSkill(mockContext, mockActionLogRepository, mockUserPreferenceDao)
    }

    @Test
    fun `shouldTrigger returns true when battery is low and next meeting is long`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Long Workshop",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(10),
            endTime = now + TimeUnit.MINUTES.toMillis(110) // 100 minutes
        )
        val model = createModel(
            now = now,
            batteryLevel = 15,
            isCharging = false,
            nextEvent = event,
            upcomingEvents = listOf(event)
        )

        assertTrue(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns true when battery is low and 2+ events in next 3 hours`() {
        val now = System.currentTimeMillis()
        val event1 = CalendarEventSummary(
            eventId = "1",
            title = "Short Sync 1",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(10),
            endTime = now + TimeUnit.MINUTES.toMillis(40)
        )
        val event2 = CalendarEventSummary(
            eventId = "2",
            title = "Short Sync 2",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(60),
            endTime = now + TimeUnit.MINUTES.toMillis(90)
        )
        val model = createModel(
            now = now,
            batteryLevel = 15,
            isCharging = false,
            nextEvent = event1,
            upcomingEvents = listOf(event1, event2)
        )

        assertTrue(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when battery is above threshold`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Long Workshop",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(10),
            endTime = now + TimeUnit.MINUTES.toMillis(110)
        )
        val model = createModel(
            now = now,
            batteryLevel = 25,
            isCharging = false,
            nextEvent = event,
            upcomingEvents = listOf(event)
        )

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when battery is charging`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Long Workshop",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(10),
            endTime = now + TimeUnit.MINUTES.toMillis(110)
        )
        val model = createModel(
            now = now,
            batteryLevel = 15,
            isCharging = true,
            nextEvent = event,
            upcomingEvents = listOf(event)
        )

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when next meeting is short and no other events in 3 hours`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Quick Chat",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(10),
            endTime = now + TimeUnit.MINUTES.toMillis(30) // 20 minutes
        )
        val model = createModel(
            now = now,
            batteryLevel = 15,
            isCharging = false,
            nextEvent = event,
            upcomingEvents = listOf(event)
        )

        assertFalse(skill.shouldTrigger(model))
    }

    private fun createModel(
        now: Long,
        batteryLevel: Int,
        isCharging: Boolean,
        nextEvent: CalendarEventSummary?,
        upcomingEvents: List<CalendarEventSummary>
    ): SituationModel {
        return SituationModel(
            currentTime = now,
            currentLocation = null,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            nextCalendarEvent = nextEvent,
            upcomingCalendarEvents = upcomingEvents
        )
    }
}
