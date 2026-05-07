package com.contextos.core.memory

import com.contextos.core.data.db.dao.ConfirmedRoutineDao
import com.contextos.core.data.db.entity.ConfirmedRoutineEntity
import com.contextos.core.data.model.NudgeMessages
import com.contextos.core.data.model.RoutineType
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that detects and surfaces personal routines.
 *
 * Provides:
 *   - Time-slot-based routine suggestion retrieval
 *   - Manual routine seeding ("Teach me faster")
 *   - Named routine type support
 *
 * Phase 10.1 — Personal Routine Detector
 */
@Singleton
class PersonalRoutineService @Inject constructor(
    private val confirmedRoutineDao: ConfirmedRoutineDao,
) {

    /**
     * Returns routine suggestions for the current time.
     *
     * Filters by:
     *   - Active routines only
     *   - Confidence >= 0.75
     *   - Matching current day-of-week and time slot (±5 minutes via DB query)
     *
     * @param currentTimeMs  Epoch millis; defaults to now.
     * @return Sorted list of suggestions, highest confidence first.
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
                val routineType = try {
                    RoutineType.valueOf(entity.routineType)
                } catch (_: Exception) {
                    RoutineType.GENERIC
                }
                RoutineSuggestion(
                    routineType = entity.routineType,
                    confidence = entity.confidence,
                    suggestedAction = entity.suggestedAction.ifBlank {
                        NudgeMessages.getMessageForRoutine(routineType)
                    },
                    id = entity.id,
                    displayName = routineType.displayName,
                )
            }
    }

    /**
     * Returns all active confirmed routines.
     */
    suspend fun getAllActiveRoutines(): List<RoutineSuggestion> {
        val entities = confirmedRoutineDao.getAllActive().first()
        return entities.map { entity ->
            val routineType = try {
                RoutineType.valueOf(entity.routineType)
            } catch (_: Exception) {
                RoutineType.GENERIC
            }
            RoutineSuggestion(
                routineType = entity.routineType,
                confidence = entity.confidence,
                suggestedAction = entity.suggestedAction,
                id = entity.id,
                displayName = routineType.displayName,
            )
        }
    }

    /**
     * Seeds a confirmed routine directly (for "Teach me faster" feature).
     *
     * Phase 10.3 — Accelerated Learning UI
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

    /**
     * Toggles a routine's active status.
     */
    suspend fun toggleRoutine(id: Long, active: Boolean) {
        confirmedRoutineDao.setActive(id, active)
    }

    /**
     * Permanently deletes a routine.
     */
    suspend fun deleteRoutine(id: Long) {
        confirmedRoutineDao.delete(id)
    }

    /**
     * Returns the count of active confirmed routines.
     */
    suspend fun getActiveRoutineCount(): Int {
        return confirmedRoutineDao.getAllActive().first().size
    }
}

data class RoutineSuggestion(
    val routineType: String,
    val confidence: Float,
    val suggestedAction: String,
    val id: Long,
    val displayName: String = routineType,
)
