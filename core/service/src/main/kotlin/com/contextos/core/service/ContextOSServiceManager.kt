package com.contextos.core.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Convenience helper for starting and stopping [ContextOSService].
 *
 * Call [startService] from any context-aware component (Activity, BroadcastReceiver, etc.).
 * Call [stopService] to gracefully shut the service down.
 */
object ContextOSServiceManager {

    /**
     * Starts [ContextOSService] as a foreground service.
     * Uses [ContextCompat.startForegroundService] to handle API-level differences.
     */
    fun startService(context: Context) {
        val intent = Intent(context, ContextOSService::class.java).apply {
            action = ContextOSService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Sends [ContextOSService.ACTION_STOP] to gracefully stop the running service.
     */
    fun stopService(context: Context) {
        val intent = Intent(context, ContextOSService::class.java).apply {
            action = ContextOSService.ACTION_STOP
        }
        context.startService(intent)
    }
}
