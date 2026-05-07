package com.contextos.core.network

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.core.content.edit
import com.contextos.core.data.db.dao.CalendarEventCacheDao
import com.contextos.core.data.db.entity.CalendarEventCacheEntity
import org.json.JSONArray
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that syncs Google Calendar events into the Room cache.
 *
 * Schedule: at service startup and every 30 minutes thereafter.
 * Rate limit budget: max 4 calls/hour (per Phase 1.5).
 */
@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val calendarApiClient: CalendarApiClient,
    private val calendarEventCacheDao: CalendarEventCacheDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Calendar sync worker started")
        return try {
            if (!consumeRateLimitBudget()) {
                Log.i(TAG, "Calendar sync skipped to stay within 4 calls/hour budget")
                return Result.success()
            }

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val syncToken = prefs.getString(KEY_SYNC_TOKEN, null)
            var result = calendarApiClient.syncUpcomingEvents(hoursAhead = 8, syncToken = syncToken)
            if (result.tokenExpired) {
                prefs.edit { remove(KEY_SYNC_TOKEN) }
                result = calendarApiClient.syncUpcomingEvents(hoursAhead = 8, syncToken = null)
            }

            val nowMs  = System.currentTimeMillis()

            val entities = result.events.map { event ->
                CalendarEventCacheEntity(
                    eventId       = event.eventId,
                    title         = event.title,
                    startTime     = event.startTime,
                    endTime       = event.endTime,
                    location      = event.location,
                    attendeesJson = JSONArray(event.attendees).toString(),
                    meetingLink   = event.meetingLink,
                    isVirtual     = event.isVirtual,
                    lastFetched   = nowMs,
                )
            }

            calendarEventCacheDao.upsertAll(entities)
            result.deletedEventIds.forEach { eventId ->
                calendarEventCacheDao.deleteByEventId(eventId)
            }
            calendarEventCacheDao.deleteEventsBefore(nowMs)
            result.nextSyncToken?.let { token ->
                prefs.edit { putString(KEY_SYNC_TOKEN, token) }
            }

            Log.i(TAG, "Calendar sync complete — ${entities.size} events cached")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Calendar sync failed — will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CalendarSyncWorker"
        private const val PREFS_NAME = "contextos_calendar_sync"
        private const val KEY_SYNC_TOKEN = "sync_token"
        private const val KEY_LAST_API_CALL_MS = "last_api_call_ms"
        private const val MIN_API_CALL_INTERVAL_MS = 15L * 60 * 1_000
        const val WORK_NAME = "contextos_calendar_sync"
        private const val STARTUP_WORK_NAME = "contextos_calendar_sync_startup"

        /**
         * Enqueues a 30-minute periodic sync task.
         * Uses [ExistingPeriodicWorkPolicy.KEEP] to avoid duplicate workers.
         */
        fun schedule(context: Context) {
            val immediateRequest = OneTimeWorkRequestBuilder<CalendarSyncWorker>().build()
            val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
                repeatInterval = 30L,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                STARTUP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                immediateRequest,
            )
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    private fun consumeRateLimitBudget(): Boolean {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nowMs = System.currentTimeMillis()
        val lastApiCallMs = prefs.getLong(KEY_LAST_API_CALL_MS, 0L)
        if (nowMs - lastApiCallMs < MIN_API_CALL_INTERVAL_MS) return false

        prefs.edit { putLong(KEY_LAST_API_CALL_MS, nowMs) }
        return true
    }
}
