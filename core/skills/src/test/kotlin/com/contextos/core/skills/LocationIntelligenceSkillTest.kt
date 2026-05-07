package com.contextos.core.skills

import com.contextos.core.data.model.SituationModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationIntelligenceSkillTest {

    private lateinit var skill: LocationIntelligenceSkill

    @Before
    fun setup() {
        skill = LocationIntelligenceSkill()
    }

    @Test
    fun `shouldTrigger always returns true`() {
        val model = SituationModel(
            currentTime = System.currentTimeMillis(),
            currentLocation = null,
            batteryLevel = 50,
            isCharging = false,
            nextCalendarEvent = null
        )

        assertTrue(skill.shouldTrigger(model))
    }
}
