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
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timeSlotMs
        val minutes = calendar.get(java.util.Calendar.MINUTE)
        val roundedMinutes = if (minutes < 30) 0 else 30
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val timeSlot = String.format("%02d:%02d", hour, roundedMinutes)

        val existing = dao.getByDayAndSlot(dayOfWeek, timeSlot)
        if (existing != null) {
            val newCount = existing.observationCount + 1
            val isSameActivity = existing.expectedActivity == activity
            val newConfidence = if (isSameActivity) {
                minOf(1.0f, existing.confidence + 0.1f)
            } else {
                maxOf(0.1f, existing.confidence - 0.2f)
            }
            val newActivity = if (newConfidence < 0.4f) activity else existing.expectedActivity
            val updated = existing.copy(
                expectedActivity = newActivity,
                confidence = newConfidence,
                observationCount = newCount,
                lastObservedMs = timeSlotMs
            )
            dao.upsert(updated)
        } else {
            val entity = RoutineMemoryEntity(
                dayOfWeek = dayOfWeek,
                timeSlot = timeSlot,
                expectedActivity = activity,
                confidence = 0.3f, // Initial confidence
                observationCount = 1,
                lastObservedMs = timeSlotMs
            )
            dao.upsert(entity)
        }
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
        val entity = dao.getByDayAndSlot(dayOfWeek, timeSlot)
        if (entity != null && entity.confidence >= 0.5f) {
            return Pair(entity.expectedActivity, entity.confidence)
        }
        return null
    }

    /**
     * Returns all high-confidence routines (confidence >= 0.7).
     * Safe to call from any coroutine context; Room dispatches I/O internally.
     */
    suspend fun getLearnedRoutines(): List<RoutineMemoryEntity> {
        return dao.getAllHighConfidence(0.7f)
    }
}