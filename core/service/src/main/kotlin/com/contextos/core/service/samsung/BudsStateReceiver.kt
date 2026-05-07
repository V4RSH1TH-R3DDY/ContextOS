package com.contextos.core.service.samsung

import android.util.Log
import com.contextos.core.data.db.dao.BudsContextDao
import com.contextos.core.data.db.entity.BudsContextEntity
import com.contextos.core.data.db.entity.BudsWearState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receives and stores state data from paired Galaxy Buds
 * via the Samsung Accessory SDK.
 *
 * Data signals consumed:
 *   - BUDS_IN_EAR (both/one/neither) — both = likely on a call or in a meeting
 *   - AMBIENT_SOUND_MODE — if enabled, user may be commuting
 *   - ACTIVE_NOISE_CANCELLATION — ANC on = noisy environment (commuting, café)
 *
 * Context inference mapping for ReasoningBuilder:
 *   - Both Buds in ear → suppress dnd_setter (user managing audio manually)
 *   - ANC active → add "Active noise cancellation on (likely commuting)" to reasoning
 *   - Neither Buds in ear during scheduled meeting → anomaly flag
 *
 * Fallback: If no Galaxy Buds paired, all fields are null, audio context
 * falls back to the existing AudioRecord-based ambient classifier.
 *
 * Phase 11.2 — Galaxy Buds Integration
 */
@Singleton
class BudsStateReceiver @Inject constructor(
    private val budsContextDao: BudsContextDao,
) {

    /**
     * Records a buds state snapshot.
     *
     * In a full implementation, this would be called from a BroadcastReceiver
     * for Samsung Accessory Framework events.
     */
    suspend fun onStateChanged(
        budsInEar: BudsWearState,
        ancActive: Boolean,
        ambientSoundActive: Boolean,
        deviceConnected: Boolean,
    ) {
        val entity = BudsContextEntity(
            timestamp = System.currentTimeMillis(),
            budsInEar = budsInEar.name,
            ancActive = ancActive,
            ambientSoundActive = ambientSoundActive,
            deviceConnected = deviceConnected,
        )
        budsContextDao.insert(entity)
        Log.d(TAG, "Buds state recorded: inEar=$budsInEar, ANC=$ancActive, ambient=$ambientSoundActive")
    }

    /**
     * Returns the most recent buds context, or null if no buds are paired.
     */
    suspend fun getLatestContext(): BudsContextEntity? {
        return budsContextDao.getLatest()
    }

    /**
     * Returns context inferences for the ReasoningBuilder.
     *
     * @return A list of reasoning strings to add to the situation model,
     *         or an empty list if no buds data is available.
     */
    suspend fun getContextInferences(): BudsInference {
        val latest = getLatestContext() ?: return BudsInference.EMPTY

        // Only use data from the last 10 minutes
        val tenMinAgo = System.currentTimeMillis() - (10 * 60 * 1000)
        if (latest.timestamp < tenMinAgo || !latest.deviceConnected) {
            return BudsInference.EMPTY
        }

        val reasoningPoints = mutableListOf<String>()
        val anomalyFlags = mutableListOf<String>()
        val budsState = try { BudsWearState.valueOf(latest.budsInEar) } catch (_: Exception) { BudsWearState.NEITHER }

        // ANC active → likely commuting
        if (latest.ancActive) {
            reasoningPoints.add("Active noise cancellation on (likely commuting)")
        }

        // Ambient sound mode → also suggests commuting
        if (latest.ambientSoundActive) {
            reasoningPoints.add("Ambient sound mode enabled on Galaxy Buds")
        }

        // Both buds in ear → user is managing audio manually
        val suppressDnd = budsState == BudsWearState.BOTH

        return BudsInference(
            reasoningPoints = reasoningPoints,
            anomalyFlags = anomalyFlags,
            suppressDndSetter = suppressDnd,
            budsConnected = true,
            budsState = budsState,
        )
    }

    /**
     * Checks if buds are removed during a scheduled meeting (anomaly).
     */
    suspend fun checkMeetingAnomaly(isInMeeting: Boolean): String? {
        if (!isInMeeting) return null
        val latest = getLatestContext() ?: return null
        val tenMinAgo = System.currentTimeMillis() - (10 * 60 * 1000)
        if (latest.timestamp < tenMinAgo || !latest.deviceConnected) return null

        val budsState = try { BudsWearState.valueOf(latest.budsInEar) } catch (_: Exception) { return null }
        if (budsState == BudsWearState.NEITHER) {
            return "Buds removed — user may not be taking this call"
        }
        return null
    }

    /**
     * Cleans up old buds data (older than 24 hours).
     */
    suspend fun cleanup() {
        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val deleted = budsContextDao.deleteOlderThan(cutoff)
        Log.d(TAG, "Cleaned up $deleted old buds context entries")
    }

    companion object {
        private const val TAG = "BudsStateReceiver"
    }
}

/**
 * Inference results from Galaxy Buds context analysis.
 */
data class BudsInference(
    val reasoningPoints: List<String>,
    val anomalyFlags: List<String>,
    val suppressDndSetter: Boolean,
    val budsConnected: Boolean,
    val budsState: BudsWearState,
) {
    companion object {
        val EMPTY = BudsInference(
            reasoningPoints = emptyList(),
            anomalyFlags = emptyList(),
            suppressDndSetter = false,
            budsConnected = false,
            budsState = BudsWearState.NEITHER,
        )
    }
}
