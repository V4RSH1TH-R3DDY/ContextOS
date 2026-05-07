package com.contextos.core.skills

import android.content.Context
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.data.model.LatLng
import com.contextos.core.data.model.SituationModel
import com.contextos.core.network.MapsDistanceMatrixClient
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class NavigationLauncherSkillTest {

    private lateinit var skill: NavigationLauncherSkill
    private val mockContext = mockk<Context>()
    private val mockMapsClient = mockk<MapsDistanceMatrixClient>()
    private val mockUserPreferenceDao = mockk<UserPreferenceDao>()

    @Before
    fun setup() {
        skill = NavigationLauncherSkill(mockContext, mockMapsClient, mockUserPreferenceDao)
    }

    @Test
    fun `shouldTrigger returns true when in-person meeting starts within 30 minutes`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Client Lunch",
            location = "123 Main St",
            startTime = now + TimeUnit.MINUTES.toMillis(20),
            endTime = now + TimeUnit.MINUTES.toMillis(80),
            isVirtual = false,
            meetingLink = null
        )
        // Null currentLocation bypasses the distance check and returns true
        val model = createModel(now, event, null)

        assertTrue(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when meeting has no location`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Client Lunch",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(20),
            endTime = now + TimeUnit.MINUTES.toMillis(80)
        )
        val model = createModel(now, event, null)

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when meeting is virtual`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Zoom Sync",
            location = "Virtual",
            startTime = now + TimeUnit.MINUTES.toMillis(20),
            endTime = now + TimeUnit.MINUTES.toMillis(80),
            isVirtual = true,
            meetingLink = "https://zoom.us/j/12345"
        )
        val model = createModel(now, event, null)

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when meeting starts in more than 30 minutes`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Client Lunch",
            location = "123 Main St",
            startTime = now + TimeUnit.MINUTES.toMillis(45),
            endTime = now + TimeUnit.MINUTES.toMillis(105)
        )
        val model = createModel(now, event, null)

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when already at destination`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Client Lunch",
            location = "123 Main St",
            startTime = now + TimeUnit.MINUTES.toMillis(20),
            endTime = now + TimeUnit.MINUTES.toMillis(80)
        )
        // Providing 0.0, 0.0 as current location will result in distance 0.0 which is < 500m
        val model = createModel(now, event, LatLng(0.0, 0.0))

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns true when far from destination`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Client Lunch",
            location = "123 Main St",
            startTime = now + TimeUnit.MINUTES.toMillis(20),
            endTime = now + TimeUnit.MINUTES.toMillis(80)
        )
        // Providing 10.0, 10.0 will result in a large distance > 500m
        val model = createModel(now, event, LatLng(10.0, 10.0))

        assertTrue(skill.shouldTrigger(model))
    }

    private fun createModel(
        now: Long,
        nextEvent: CalendarEventSummary?,
        currentLocation: LatLng?
    ): SituationModel {
        return SituationModel(
            currentTime = now,
            currentLocation = currentLocation,
            batteryLevel = 50,
            isCharging = false,
            nextCalendarEvent = nextEvent
        )
    }
}
