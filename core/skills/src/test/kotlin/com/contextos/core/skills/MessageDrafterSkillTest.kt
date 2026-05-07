package com.contextos.core.skills

import android.content.Context
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.data.model.MessageDrafter
import com.contextos.core.data.model.SituationModel
import com.contextos.core.data.repository.ActionLogRepository
import com.contextos.core.network.MapsDistanceMatrixClient
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class MessageDrafterSkillTest {

    private lateinit var skill: MessageDrafterSkill
    private val mockContext = mockk<Context>()
    private val mockActionLogRepository = mockk<ActionLogRepository>()
    private val mockUserPreferenceDao = mockk<UserPreferenceDao>()
    private val mockMessageDrafter = mockk<MessageDrafter>()
    private val mockMapsClient = mockk<MapsDistanceMatrixClient>()

    @Before
    fun setup() {
        skill = MessageDrafterSkill(
            mockContext,
            mockActionLogRepository,
            mockUserPreferenceDao,
            mockMessageDrafter,
            mockMapsClient
        )
    }

    @Test
    fun `shouldTrigger returns true when meeting with location starts within 15 minutes`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Project Sync",
            location = "Room 404",
            startTime = now + TimeUnit.MINUTES.toMillis(10),
            endTime = now + TimeUnit.MINUTES.toMillis(70)
        )
        val model = createModel(now, event)

        assertTrue(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when meeting has no location`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Project Sync",
            location = null,
            startTime = now + TimeUnit.MINUTES.toMillis(10),
            endTime = now + TimeUnit.MINUTES.toMillis(70)
        )
        val model = createModel(now, event)

        assertFalse(skill.shouldTrigger(model))
    }

    @Test
    fun `shouldTrigger returns false when meeting starts in more than 15 minutes`() {
        val now = System.currentTimeMillis()
        val event = CalendarEventSummary(
            eventId = "1",
            title = "Project Sync",
            location = "Room 404",
            startTime = now + TimeUnit.MINUTES.toMillis(20),
            endTime = now + TimeUnit.MINUTES.toMillis(80)
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
