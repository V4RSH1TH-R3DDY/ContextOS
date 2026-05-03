package com.contextos.core.data.repository

import com.contextos.core.data.db.dao.ActionLogDao
import com.contextos.core.data.db.entity.ActionLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Repository that wraps [ActionLogDao] and exposes a coroutine-friendly API
 * to the rest of the application.
 */
class ActionLogRepository @Inject constructor(
    private val dao: ActionLogDao,
) {

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
}
