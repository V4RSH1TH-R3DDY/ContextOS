package com.contextos.core.service

import android.util.Log
import com.contextos.core.data.db.entity.ActionLogEntity
import com.contextos.core.data.model.ActionOutcome
import com.contextos.core.data.repository.ActionLogRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks service uptime and cycle count; writes periodic heartbeat entries to the ActionLog.
 *
 * Phase 1.1 deliverable: a persistent service that logs a heartbeat to the action log.
 */
@Singleton
class ServiceHealthMonitor @Inject constructor(
    private val actionLogRepository: ActionLogRepository,
) {

    private val serviceStartTimeMs = AtomicLong(0L)
    private val cycleCount         = AtomicInteger(0)

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle events
    // ─────────────────────────────────────────────────────────────────────────

    /** Call once when the foreground service starts. */
    fun recordServiceStart() {
        serviceStartTimeMs.set(System.currentTimeMillis())
        Log.i(TAG, "Service started — uptime clock reset")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metrics
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns milliseconds since [recordServiceStart] was called; 0 if not yet started. */
    fun getUptimeMs(): Long {
        val start = serviceStartTimeMs.get()
        return if (start > 0L) System.currentTimeMillis() - start else 0L
    }

    /** Increments the cycle counter. Call once per completed agent cycle. */
    fun recordCycleComplete() {
        cycleCount.incrementAndGet()
    }

    /** Returns the total number of completed agent cycles since last service start. */
    fun getCycleCount(): Int = cycleCount.get()

    // ─────────────────────────────────────────────────────────────────────────
    // Heartbeat
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inserts a heartbeat entry into the ActionLog.
     * Callers should throttle this to avoid DB spam (e.g., every 10 cycles).
     */
    suspend fun reportHeartbeat() {
        val uptimeSec  = getUptimeMs() / 1_000
        val cycles     = getCycleCount()
        val description = "Service uptime: ${uptimeSec}s | Cycles: $cycles"

        Log.d(TAG, description)

        try {
            actionLogRepository.insert(
                ActionLogEntity(
                    timestampMs      = System.currentTimeMillis(),
                    skillId          = HEARTBEAT_SKILL_ID,
                    skillName        = "System Heartbeat",
                    description      = description,
                    wasAutoApproved  = true,
                    userOverride     = null,
                    situationSnapshot = "{}",
                    reasoningPayload  = "{}",
                    outcome          = ActionOutcome.SUCCESS.name,
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write heartbeat to ActionLog", e)
        }
    }

    companion object {
        private const val TAG               = "ServiceHealthMonitor"
        const val HEARTBEAT_SKILL_ID        = "system.heartbeat"
    }
}
