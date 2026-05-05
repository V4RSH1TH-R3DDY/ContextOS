package com.contextos.core.memory

import com.contextos.core.data.db.dao.ConfirmedRoutineDao
import com.contextos.core.data.db.entity.ConfirmedRoutineEntity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonalRoutineService @Inject constructor(
    private val confirmedRoutineDao: ConfirmedRoutineDao,
) {
    
    /**
     * Returns routine suggestions for the current time
     */
    suspend fun getSuggestionsForNow(
        currentTimeMs: Long = System.currentTimeMillis(),
    ): List<RoutineSuggestion> {
        val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 2=Monday, etc.
        val timeMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        
        val routines = confirmedRoutineDao.getForTimeSlot(dayOfWeek, timeMinutes)
        
        return routines
            .filter { it.isActive && it.confidence >= 0.75f }
            .sortedByDescending { it.lastObservedMs }
            .map { entity ->
                RoutineSuggestion(
                    routineType = entity.routineType,
                    confidence = entity.confidence,
                    suggestedAction = entity.suggestedAction,
                    id = entity.id,
                )
            }
    }
    
    /**
     * Seeds a confirmed routine directly (for "Teach me faster" feature)
     */
    suspend fun seedRoutine(
        routineType: String,
        dayOfWeek: Int,
        timeSlotStart: Int,
        timeSlotEnd: Int,
        suggestedAction: String,
    ): Long {
        val entity = ConfirmedRoutineEntity(
            routineType = routineType,
            dayOfWeek = dayOfWeek,
            timeSlotStart = timeSlotStart,
            timeSlotEnd = timeSlotEnd,
            confidence = 1.0f,
            observationCount = 5, // Skip observation requirement
            lastObservedMs = System.currentTimeMillis(),
            suggestedAction = suggestedAction,
            isActive = true,
        )
        return confirmedRoutineDao.insert(entity)
    }
}

data class RoutineSuggestion(
    val routineType: String,
    val confidence: Float,
    val suggestedAction: String,
    val id: Long,
)
