package com.contextos.core.service.agent

import android.util.Log
import com.contextos.core.service.SensorDataCollector
import com.contextos.core.service.ServiceHealthMonitor
import com.contextos.core.service.SituationModelBuilder
import com.contextos.core.skills.Skill
import com.contextos.core.skills.SkillRegistry
import com.contextos.core.skills.SkillResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The central ContextOS agent orchestrator.
 *
 * On every call to [runCycle]:
 * 1. Collects raw sensor data via [SensorDataCollector]
 * 2. Builds the [SituationModel] via [SituationModelBuilder]
 * 3. Evaluates all registered [Skill]s via [SkillRegistry.skills]
 * 4. For each triggered skill, calls [Skill.execute] and routes the result through [ActionDispatcher]
 * 5. Records cycle completion and periodically writes a heartbeat via [ServiceHealthMonitor]
 *
 * The full cycle must complete within [CYCLE_TIMEOUT_MS] (12 s) per the Cycle Budget in contracts.md.
 */
@Singleton
class ContextAgent @Inject constructor(
    private val sensorCollector:  SensorDataCollector,
    private val modelBuilder:     SituationModelBuilder,
    private val skillRegistry:    SkillRegistry,
    private val dispatcher:       ActionDispatcher,
    private val healthMonitor:    ServiceHealthMonitor,
) {

    /**
     * Runs one complete agent cycle.
     *
     * Safe to call from any coroutine; wraps the body in [withTimeout] to enforce
     * the 12-second budget, and catches unexpected exceptions to prevent crashing the loop.
     */
    suspend fun runCycle() {
        try {
            withTimeout(CYCLE_TIMEOUT_MS) {
                executeCycle()
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Agent cycle exceeded ${CYCLE_TIMEOUT_MS}ms budget; continuing next cycle")
        } catch (e: CancellationException) {
            // Re-throw so the caller's coroutine scope can handle structured cancellation
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agent cycle failed unexpectedly", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cycle implementation
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun executeCycle() {
        Log.i(TAG, "▶ Cycle ${healthMonitor.getCycleCount() + 1} started")

        // Step 1 — Collect sensor data
        val raw = try {
            sensorCollector.collect()
        } catch (e: Exception) {
            Log.e(TAG, "Sensor collection failed — skipping cycle", e)
            return
        }

        // Step 2 — Build situation model
        val model = try {
            modelBuilder.build(raw)
        } catch (e: Exception) {
            Log.e(TAG, "SituationModel construction failed — skipping cycle", e)
            return
        }

        // Step 3 — Evaluate and execute triggered skills
        val allSkills = skillRegistry.skills
        val triggeredSkills = mutableListOf<Skill>()
        val dismissedSkills = mutableListOf<Pair<Skill, String>>()

        for (skill in allSkills) {
            try {
                if (skill.shouldTrigger(model)) {
                    triggeredSkills.add(skill)
                } else {
                    dismissedSkills.add(Pair(skill, "Skill '${skill.id}' did not trigger"))
                }
            } catch (e: Exception) {
                Log.w(TAG, "shouldTrigger() threw for skill '${skill.id}'", e)
                dismissedSkills.add(Pair(skill, "Evaluation error: ${e.message}"))
            }
        }

        Log.i(TAG, "Cycle: ${triggeredSkills.size} skill(s) triggered, ${dismissedSkills.size} dismissed")

        // Step 4 — Execute triggered skills (sequential to avoid parallel DB writes)
        for (skill in triggeredSkills) {
            val result = try {
                skill.execute(model)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Skill '${skill.id}' threw during execute()", e)
                SkillResult.Failure("Unexpected error in ${skill.id}: ${e.message}", e)
            }
            dispatcher.dispatch(skill, result, model, confidence = 0.85f)
        }

        // Step 4b — Log dismissed skills with reasoning (collapsed by default in UI)
        for ((skill, reason) in dismissedSkills) {
            dispatcher.dispatch(
                skill = skill,
                result = SkillResult.Skipped(reason),
                model = model,
                confidence = 0.30f,
            )
        }

        // Step 5 — Record cycle & periodic heartbeat
        healthMonitor.recordCycleComplete()
        if (healthMonitor.getCycleCount() % HEARTBEAT_EVERY_N_CYCLES == 0) {
            healthMonitor.reportHeartbeat()
        }

        Log.i(TAG, "✔ Cycle ${healthMonitor.getCycleCount()} complete " +
                "(battery=${model.batteryLevel}%, location=${model.locationLabel}, context=${model.analysis?.currentContextLabel ?: "Unknown"})")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        private const val TAG                   = "ContextAgent"
        private const val CYCLE_TIMEOUT_MS      = 12_000L  // 12-second budget (contracts.md §7.1)
        private const val HEARTBEAT_EVERY_N_CYCLES = 10    // ~every 150 minutes
    }
}
