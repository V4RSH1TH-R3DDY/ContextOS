package com.contextos.core.skills

import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.data.model.SituationModel
import com.contextos.core.network.DriveApiClient
import com.contextos.core.network.GmailApiClient
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class DocumentFetcherSkillTest {

    private lateinit var skill: DocumentFetcherSkill
    private val mockGmailApiClient = mockk<GmailApiClient>()
    private val mockDriveApiClient = mockk<DriveApiClient>()

    @Before
    fun setup() {
        skill = DocumentFetcherSkill(mockGmailApiClient, mockDriveApiClient)
    }

    @Test
    fun `shouldTrigger returns true when meeting with keywords starts within 20 minutes`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Q3 Performance Review",
            location = "Room 404",
            startTime = now + TimeUnit.MINUTES.toMillis(15),
            endTime = now + TimeUnit.MINUTES.toMillis(75)
        )
        val model = createModel(now, event)

        assertTrue(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when meeting has no keywords`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Weekly Catchup",
            location = "Room 404",
            startTime = now + TimeUnit.MINUTES.toMillis(15),
            endTime = now + TimeUnit.MINUTES.toMillis(75)
        )
        val model = createModel(now, event)

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when meeting starts in more than 20 minutes`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Q3 Performance Review",
            location = "Room 404",
            startTime = now + TimeUnit.MINUTES.toMillis(25),
            endTime = now + TimeUnit.MINUTES.toMillis(85)
        )
        val model = createModel(now, event)

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when there is no upcoming event`() {
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
            nextCalendarEvent = event
        )
    }
}
