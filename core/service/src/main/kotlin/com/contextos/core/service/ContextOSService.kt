package com.contextos.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.contextos.core.skills.SkillRegistry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Long-running foreground service that hosts the ContextOS agent loop.
 * Started via [ContextOSServiceManager]; uses [SkillRegistry] to evaluate and
 * dispatch skills on every heartbeat cycle.
 */
@AndroidEntryPoint
class ContextOSService : Service() {

    @Inject
    lateinit var skillRegistry: SkillRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        serviceScope.launch {
            while (true) {
                Log.i(TAG, "ContextOS heartbeat — cycle started")
                // TODO: build SituationModel and evaluate skills via skillRegistry
                delay(HEARTBEAT_INTERVAL_MS)
            }
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
        private const val HEARTBEAT_INTERVAL_MS = 15L * 60 * 1_000 // 15 minutes

        const val ACTION_START = "com.contextos.START"
        const val ACTION_STOP = "com.contextos.STOP"
        const val NOTIFICATION_ID = 1001
    }
}
