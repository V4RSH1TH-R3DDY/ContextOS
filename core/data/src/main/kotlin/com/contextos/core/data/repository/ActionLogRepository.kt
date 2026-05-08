package com.contextos.core.data.repository

import com.contextos.core.data.db.dao.ActionLogDao
import com.contextos.core.data.db.entity.ActionLogEntity
import com.contextos.core.data.model.ReasoningPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Repository that wraps [ActionLogDao] and exposes a coroutine-friendly API
 * to the rest of the application.
 */
class ActionLogRepository @Inject constructor(
    private val dao: ActionLogDao,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Inserts a new [ActionLogEntity] and returns the generated row id.
     */
    suspend fun insert(entity: ActionLogEntity): Long = dao.insert(entity)

    /**
     * Returns a [Flow] that emits the full action log ordered by most-recent first.
     */
    fun getAll(): Flow<List<ActionLogEntity>> = dao.getAll()

    /**
     * Returns a [Flow] that emits the [limit] most-recent log entries.
     */
    fun getRecent(limit: Int): Flow<List<ActionLogEntity>> = dao.getRecent(limit)

    /**
     * Returns a [Flow] that emits all log entries for the given [skillId].
     */
    fun getBySkillId(skillId: String): Flow<List<ActionLogEntity>> =
        dao.getBySkillId(skillId)

    /**
     * Deletes all log entries older than [timestampMs] (epoch millis).
     * Returns the number of rows deleted.
     */
    suspend fun deleteOlderThan(timestampMs: Long): Int = dao.deleteOlderThan(timestampMs)

    suspend fun countSuccessfulTriggersSince(skillId: String, sinceMs: Long): Int =
        dao.countSuccessfulTriggersSince(skillId, sinceMs)

    suspend fun countDismissalsSince(skillId: String, sinceMs: Long): Int =
        dao.countDismissalsSince(skillId, sinceMs)

    /**
     * Updates the [userOverride] for an action log entry by its id.
     * Used when the user approves or dismisses a pending action from the UI.
     */
    suspend fun updateWithUserOverride(id: Long, userOverride: String): Int =
        dao.updateUserOverride(id, userOverride)

    /**
     * Pre-populates demo reasoning entries for Phase 9.3 demo mode.
     */
    suspend fun prePopulateDemoReasoningEntries() {
        val now = System.currentTimeMillis()

        val demoEntries = listOf(
            ActionLogEntity(
                timestampMs = now - 3600000, // 1 hour ago
                skillId = "navigation_launcher",
                skillName = "Navigation Launcher",
                description = "Launching navigation to Acme Corp meeting",
                wasAutoApproved = false,
                userOverride = null,
                situationSnapshot = "{}",
                reasoningPayload = json.encodeToString(
                    ReasoningPayload(
                        contextLabel = "Pre-meeting commute",
                        confidenceScore = 0.92f,
                        reasoningPoints = listOf(
                            "Meeting in 14 mins",
                            "You are 8 km away",
                            "Traffic is currently heavy",
                            "Historical lateness pattern detected"
                        ),
                        anomalyFlags = listOf("Meeting in 8 mins but you appear to be 20 km away"),
                        dataSourcesUsed = listOf("Calendar", "GPS", "Your history")
                    )
                ),
                outcome = "PENDING_USER_CONFIRMATION"
            ),
            ActionLogEntity(
                timestampMs = now - 7200000, // 2 hours ago
                skillId = "battery_warner",
                skillName = "Battery Warner",
                description = "Warning: Battery low before long meeting",
                wasAutoApproved = true,
                userOverride = null,
                situationSnapshot = "{}",
                reasoningPayload = json.encodeToString(
                    ReasoningPayload(
                        contextLabel = "Low battery before long meeting",
                        confidenceScore = 0.88f,
                        reasoningPoints = listOf(
                            "Battery at 18%",
                            "Long meeting (2 hours) starting in 30 mins",
                            "No charger nearby detected"
                        ),
                        dataSourcesUsed = listOf("Battery", "Calendar")
                    )
                ),
                outcome = "SUCCESS"
            ),
            ActionLogEntity(
                timestampMs = now - 10800000, // 3 hours ago
                skillId = "dnd_setter",
                skillName = "DND Setter",
                description = "Enabling Do Not Disturb for evening commute",
                wasAutoApproved = true,
                userOverride = null,
                situationSnapshot = "{}",
                reasoningPayload = json.encodeToString(
                    ReasoningPayload(
                        contextLabel = "End-of-day commute home",
                        confidenceScore = 0.79f,
                        reasoningPoints = listOf(
                            "Typical commute time: 5:45 PM",
                            "Location: Office",
                            "Calendar shows no evening events"
                        ),
                        dataSourcesUsed = listOf("Calendar", "GPS", "Your history")
                    )
                ),
                outcome = "SUCCESS"
            )
        )

        demoEntries.forEach { entry ->
            dao.insert(entry)
        }
    }
}
