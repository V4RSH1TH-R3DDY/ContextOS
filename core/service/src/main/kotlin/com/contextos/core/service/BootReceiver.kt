package com.contextos.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts the ContextOS foreground service and schedules WorkManager jobs after
 * the device completes its boot sequence.
 *
 * Registered in the module's AndroidManifest with:
 *   <action android:name="android.intent.action.BOOT_COMPLETED" />
 *
 * Requires the RECEIVE_BOOT_COMPLETED permission (declared in :app AndroidManifest).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot complete — starting ContextOS service")

        // Start the foreground service
        ContextOSServiceManager.startService(context)

        // Re-schedule the WorkManager periodic worker (it persists across reboots,
        // but KEEP policy means this is a no-op if already enqueued)
        AgentCycleWorker.schedule(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
