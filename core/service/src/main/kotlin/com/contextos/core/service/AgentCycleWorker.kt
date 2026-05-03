package com.contextos.core.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.contextos.core.service.agent.ContextAgent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Battery-optimised periodic worker that triggers the ContextOS agent cycle via WorkManager.
 *
 * Complements [ContextOSService]: the service provides real-time reactivity while this worker
 * ensures the agent runs even when the service has been trimmed by the OS.
 */
@HiltWorker
class AgentCycleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val contextAgent: ContextAgent,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "ContextOS agent cycle — WorkManager trigger")
        return try {
            contextAgent.runCycle()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Agent cycle failed in WorkManager", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AgentCycleWorker"

        /** Unique name used to identify this periodic work in the WorkManager queue. */
        const val WORK_NAME = "contextos_agent_cycle"

        /**
         * Enqueues a 15-minute periodic work request.
         * Uses [ExistingPeriodicWorkPolicy.KEEP] so a running request is never replaced.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AgentCycleWorker>(
                repeatInterval         = 15L,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
