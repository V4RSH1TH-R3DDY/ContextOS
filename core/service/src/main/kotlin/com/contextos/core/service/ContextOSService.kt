package com.contextos.core.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.contextos.core.network.CalendarSyncWorker
import com.contextos.core.service.agent.ContextAgent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Long-running foreground service that hosts the ContextOS agent loop.
 * Started via [ContextOSServiceManager]; delegates all sensing and skill evaluation
 * to [ContextAgent] on every heartbeat cycle.
 *
 * A backup [AlarmManager] alarm and a [AgentCycleWorker] WorkManager job are also
 * scheduled so the agent runs even when the OS trims the process.
 */
@AndroidEntryPoint
class ContextOSService : Service() {

    @Inject lateinit var contextAgent: ContextAgent
    @Inject lateinit var healthMonitor: ServiceHealthMonitor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var agentLoopJob: Job? = null

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START or null — start / resume the service
                startForeground(NOTIFICATION_ID, buildNotification())
                startAgentLoop()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // -------------------------------------------------------------------------
    // Agent loop
    // -------------------------------------------------------------------------

    private fun startAgentLoop() {
        if (agentLoopJob?.isActive == true) {
            Log.d(TAG, "Agent loop already running")
            return
        }

        agentLoopJob = serviceScope.launch {
            healthMonitor.recordServiceStart()
            AgentCycleWorker.schedule(applicationContext)
            CalendarSyncWorker.schedule(applicationContext)
            scheduleExactAlarm()
            while (true) {
                try {
                    contextAgent.runCycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Agent cycle error", e)
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Exact alarm (wakeup backup for Doze mode)
    // -------------------------------------------------------------------------

    /**
     * Schedules a single exact alarm that will re-deliver [ACTION_START] to this service
     * [HEARTBEAT_INTERVAL_MS] from now. This ensures the service wakes up even if the OS
     * kills it while the device is in Doze mode.
     *
     * The service reschedules the alarm each time [onStartCommand] is called (including
     * when the alarm fires), so effectively creating a self-rescheduling wakeup chain.
     */
    private fun scheduleExactAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarm permission unavailable; relying on WorkManager fallback")
            return
        }

        val intent = Intent(this, ContextOSService::class.java).apply {
            action = ACTION_START
        }
        val pendingIntent = PendingIntent.getService(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MS,
                pendingIntent,
            )
            Log.d(TAG, "scheduleExactAlarm: next wakeup in ${HEARTBEAT_INTERVAL_MS / 60_000} min")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm scheduling denied; relying on WorkManager fallback", e)
        }
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ContextOS Service",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the ContextOS agent running in the background"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("ContextOS")
        .setContentText("Agent is running…")
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setOngoing(true)
        .build()

    // -------------------------------------------------------------------------
    // Companion
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "ContextOSService"
        private const val CHANNEL_ID = "contextos_service"

        /** Primary heartbeat cadence — also used as the exact-alarm interval. */
        private const val HEARTBEAT_INTERVAL_MS = 15L * 60 * 1_000 // 15 minutes

        private const val ALARM_REQUEST_CODE = 42

        const val ACTION_START = "com.contextos.START"
        const val ACTION_STOP  = "com.contextos.STOP"
        const val NOTIFICATION_ID = 1001
    }
}
