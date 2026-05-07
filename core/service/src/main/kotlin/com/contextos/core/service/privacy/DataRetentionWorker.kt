package com.contextos.core.service.privacy

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.contextos.core.data.db.dao.ActionLogDao
import com.contextos.core.data.db.dao.LocationMemoryDao
import com.contextos.core.data.preferences.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that enforces the data retention policy.
 *
 * Runs every 7 days and:
 *   - Deletes action log entries older than the configured retention period (default 90 days)
 *   - Prunes location memory entries with < 3 visits and last visit > 60 days ago
 *
 * Phase 12.2 — On-Device Storage Hardening
 */
@HiltWorker
class DataRetentionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val actionLogDao: ActionLogDao,
    private val locationMemoryDao: LocationMemoryDao,
    private val preferencesManager: PreferencesManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val retentionDays = preferencesManager.dataRetentionDays.first()
            val now = System.currentTimeMillis()

            // 1. Delete action log entries older than retention period
            val retentionMs = retentionDays.toLong() * 24 * 60 * 60 * 1000
            val cutoffMs = now - retentionMs
            val deletedLogs = actionLogDao.deleteOlderThan(cutoffMs)
            Log.i(TAG, "Retention policy: deleted $deletedLogs action log entries older than $retentionDays days")

            // 2. Prune low-visit location memory entries
            val sixtyDaysAgo = now - (60L * 24 * 60 * 60 * 1000)
            val allLocations = locationMemoryDao.getAll().first()
            var prunedLocations = 0
            allLocations.forEach { location ->
                if (location.visitCount < 3 && location.lastVisitedMs < sixtyDaysAgo) {
                    locationMemoryDao.deleteByHash(location.latLngHash)
                    prunedLocations++
                }
            }
            Log.i(TAG, "Retention policy: pruned $prunedLocations low-visit locations")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Data retention worker failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DataRetentionWorker"
        private const val WORK_NAME = "data_retention_cleanup"

        /**
         * Enqueues this worker to run every 7 days.
         */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<DataRetentionWorker>(
                7, TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.i(TAG, "Data retention worker enqueued (7-day interval)")
        }
    }
}
