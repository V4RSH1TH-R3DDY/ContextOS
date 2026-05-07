package com.contextos.core.service.samsung

import android.util.Log
import com.contextos.core.data.db.dao.WearableContextDao
import com.contextos.core.data.db.entity.WearableContextEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receives and stores activity data from a paired Galaxy Watch
 * via the Wearable Data Layer API.
 *
 * Data signals consumed:
 *   - ACTIVITY_TYPE — walking, running, stationary, in_vehicle
 *   - HEART_RATE — elevated HR + stationary = possible stress signal
 *   - STEPS — significant step increase = user has started moving
 *
 * In production, this would extend WearableListenerService.
 * Currently implemented as a repository-style class that accepts data pushes.
 *
 * Fallback: If no Galaxy Watch is paired, all fields are null and
 * SensorDataCollector skips wearable context gracefully.
 *
 * Phase 11.1 — Galaxy Watch Integration
 */
@Singleton
class WearableDataReceiver @Inject constructor(
    private val wearableContextDao: WearableContextDao,
) {

    /**
     * Records a wearable data snapshot.
     *
     * In a full implementation, this would be called from the
     * WearableListenerService's onDataChanged() callback.
     */
    suspend fun onDataReceived(
        activityType: String,
        heartRate: Int?,
        stepCountDelta: Int,
        deviceConnected: Boolean,
    ) {
        val entity = WearableContextEntity(
            timestamp = System.currentTimeMillis(),
            activityType = activityType,
            heartRate = heartRate,
            stepCountDelta = stepCountDelta,
            deviceConnected = deviceConnected,
        )
        wearableContextDao.insert(entity)
        Log.d(TAG, "Wearable data recorded: $activityType, HR=$heartRate, steps=$stepCountDelta")
    }

    /**
     * Returns the most recent wearable context, or null if no watch is paired.
     */
    suspend fun getLatestContext(): WearableContextEntity? {
        return wearableContextDao.getLatest()
    }

    /**
     * Returns a wearable context summary string for the SituationModeler prompt.
     *
     * Example output: "Galaxy Watch: Walking, 94 bpm, 850 steps in last 10 min"
     */
    suspend fun getContextSummary(): String? {
        val latest = getLatestContext() ?: return null

        // Only use data from the last 15 minutes
        val fifteenMinAgo = System.currentTimeMillis() - (15 * 60 * 1000)
        if (latest.timestamp < fifteenMinAgo) return null
        if (!latest.deviceConnected) return null

        val parts = mutableListOf<String>()
        parts.add(latest.activityType.replaceFirstChar { it.uppercase() })
        latest.heartRate?.let { parts.add("$it bpm") }
        if (latest.stepCountDelta > 0) {
            parts.add("${latest.stepCountDelta} steps in last 10 min")
        }

        return "Galaxy Watch: ${parts.joinToString(", ")}"
    }

    /**
     * Cleans up old wearable data (older than 24 hours).
     */
    suspend fun cleanup() {
        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val deleted = wearableContextDao.deleteOlderThan(cutoff)
        Log.d(TAG, "Cleaned up $deleted old wearable context entries")
    }

    companion object {
        private const val TAG = "WearableDataReceiver"
    }
}
