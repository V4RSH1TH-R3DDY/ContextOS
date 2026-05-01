package com.contextos.core.memory

import com.contextos.core.data.db.dao.RoutineMemoryDao
import com.contextos.core.data.db.entity.RoutineMemoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the routine-learning subsystem.
 *
 * Observations are collected across cycles and quantized to 30-minute time blocks
 * (e.g., "09:00", "09:30"). After enough observations the system learns
 * a confidence-weighted prediction of expected activity per day/time slot.
 *
 * Full implementation: Phase 3.2 — Routine Memory System
 */
@Singleton
class RoutineMemoryManager @Inject constructor(
    private val dao: RoutineMemoryDao,
) {
    /**
     * Record an observed activity for the given day/time slot.
     * Quantizes time to the nearest 30-minute block (e.g., 09:00 or 09:30).
     *
     * @param dayOfWeek  ISO day-of-week (1 = Monday … 7 = Sunday)
     * @param timeSlotMs Epoch millis of the observation; quantized internally.
     * @param activity   Human-readable activity label (e.g., "commuting", "meeting").
     */
    suspend fun recordObservation(dayOfWeek: Int, timeSlotMs: Long, activity: String) {
        TODO("Phase 3.2 — Routine Memory System")
    }

    /**
     * Returns the predicted activity label and confidence for the given slot.
     * Returns null if no sufficient observations exist.
     *
     * @param dayOfWeek ISO day-of-week (1 = Monday … 7 = Sunday)
     * @param timeSlot  "HH:MM" string rounded to the nearest 30-minute block.
     * @return Pair of (activity label, confidence in [0, 1]) or null.
     */
    suspend fun getPredictedActivity(dayOfWeek: Int, timeSlot: String): Pair<String, Float>? {
        TODO("Phase 3.2 — Routine Memory System")
    }

    /**
     * Returns all high-confidence routines (confidence >= 0.7).
     * Safe to call from any coroutine context; Room dispatches I/O internally.
     */
    suspend fun getLearnedRoutines(): List<RoutineMemoryEntity> {
        return dao.getAllHighConfidence(0.7f)
    }
}
