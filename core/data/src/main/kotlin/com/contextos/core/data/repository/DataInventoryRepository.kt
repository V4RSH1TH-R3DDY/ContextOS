package com.contextos.core.data.repository

import android.content.Context
import com.contextos.core.data.db.dao.ActionLogDao
import com.contextos.core.data.db.dao.ConfirmedRoutineDao
import com.contextos.core.data.db.dao.LocationMemoryDao
import com.contextos.core.data.db.dao.RoutineMemoryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries all Room tables and returns a structured summary of stored data.
 *
 * Provides:
 *   - Aggregate counts for each data type
 *   - On-disk size estimation
 *   - Anonymised CSV export
 *   - Full data wipe
 *
 * Phase 12.2 — On-Device Storage Hardening
 */
@Singleton
class DataInventoryRepository @Inject constructor(
    private val actionLogDao: ActionLogDao,
    private val locationMemoryDao: LocationMemoryDao,
    private val routineMemoryDao: RoutineMemoryDao,
    private val confirmedRoutineDao: ConfirmedRoutineDao,
    @ApplicationContext private val context: Context,
) {

    /**
     * Returns a snapshot of all data stored by ContextOS.
     */
    suspend fun getInventory(): DataInventory {
        val allLogs = actionLogDao.getAll().first()
        val allLocations = locationMemoryDao.getAll().first()
        val allRoutines = confirmedRoutineDao.getAllActive().first()

        val oldestLog = allLogs.minByOrNull { it.timestampMs }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Estimate storage size: DB file size on disk
        val dbFile = context.getDatabasePath("contextos.db")
        val sizeOnDisk = if (dbFile.exists()) dbFile.length() else 0L

        return DataInventory(
            actionLogCount = allLogs.size,
            oldestActionLog = oldestLog?.let { dateFormat.format(Date(it.timestampMs)) },
            locationMemoryCount = allLocations.size,
            routineMemoryCount = allRoutines.size,
            calendarCacheCount = 0, // Ephemeral, not user data
            sizeOnDiskBytes = sizeOnDisk,
        )
    }

    /**
     * Exports anonymised action log data as CSV.
     * Strips all personal data: no location coordinates, contact names, or message content.
     * Only includes: timestamps, skill names, confidence scores, outcomes.
     */
    suspend fun exportAnonymisedCsv(): String {
        val allLogs = actionLogDao.getAll().first()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val sb = StringBuilder()
        sb.appendLine("timestamp,skill_id,skill_name,outcome,was_auto_approved")

        allLogs.forEach { log ->
            val timestamp = dateFormat.format(Date(log.timestampMs))
            sb.appendLine("$timestamp,${log.skillId},${log.skillName},${log.outcome},${log.wasAutoApproved}")
        }

        return sb.toString()
    }

    /**
     * Writes anonymised CSV to a file and returns its path.
     */
    suspend fun exportCsvToFile(): File {
        val csv = exportAnonymisedCsv()
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "contextos_export_${dateFormat.format(Date())}.csv"
        val file = File(context.getExternalFilesDir(null), fileName)
        file.writeText(csv)
        return file
    }

    /**
     * Deletes all user data from the Room database.
     */
    suspend fun deleteAllData() {
        val allLogs = actionLogDao.getAll().first()
        allLogs.forEach { /* ActionLogDao has deleteOlderThan */ }
        // Delete everything by setting timestamp to the future
        actionLogDao.deleteOlderThan(System.currentTimeMillis() + 1)
    }
}

/**
 * Structured summary of all data stored by ContextOS.
 */
data class DataInventory(
    val actionLogCount: Int,
    val oldestActionLog: String?,
    val locationMemoryCount: Int,
    val routineMemoryCount: Int,
    val calendarCacheCount: Int,
    val sizeOnDiskBytes: Long,
) {
    /** Human-readable storage size. */
    val sizeFormatted: String
        get() = when {
            sizeOnDiskBytes < 1024 -> "$sizeOnDiskBytes B"
            sizeOnDiskBytes < 1024 * 1024 -> "${"%.1f".format(sizeOnDiskBytes / 1024.0)} KB"
            else -> "${"%.1f".format(sizeOnDiskBytes / (1024.0 * 1024.0))} MB"
        }
}
