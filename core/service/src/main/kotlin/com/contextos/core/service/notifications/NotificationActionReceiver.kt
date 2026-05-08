package com.contextos.core.service.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.contextos.core.data.model.UserOverride
import com.contextos.core.data.repository.ActionLogRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationManager: ContextOSNotificationManager

    @Inject
    lateinit var actionLogRepository: ActionLogRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val skillId = intent.getStringExtra("skill_id") ?: return
        val actionLogId = intent.getLongExtra("action_log_id", -1L)

        when (action) {
            "ACTION_APPROVE" -> handleApprove(context, skillId, actionLogId, intent)
            "ACTION_DISMISS" -> handleDismiss(skillId, actionLogId)
            "ACTION_SEND_MESSAGE" -> handleSendMessage(skillId, actionLogId, intent)
            "ACTION_SEND_BATTERY_WARNING" -> handleSendBatteryWarning(skillId, actionLogId, intent)
        }

        notificationManager.cancelAll()
    }

    private fun handleApprove(context: Context, skillId: String, actionLogId: Long, intent: Intent) {
        GlobalScope.launch {
            try {
                actionLogRepository.updateWithUserOverride(actionLogId, UserOverride.APPROVED.name)
                Log.i(TAG, "[${skillId}] User approved action (log id: $actionLogId)")
            } catch (e: Exception) {
                Log.e(TAG, "[${skillId}] Failed to update action log with approval", e)
            }
        }

        when (skillId) {
            "navigation_launcher" -> {
                val location = intent.getStringExtra("location")
                if (!location.isNullOrBlank()) {
                    launchNavigation(context, location)
                }
            }
        }
    }

    private fun launchNavigation(context: Context, destination: String) {
        try {
            val encodedDestination = Uri.encode(destination)
            val navIntent = Intent(
                Intent.ACTION_VIEW,
                "google.navigation:q=$encodedDestination&mode=d".toUri(),
            ).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(navIntent)
            Log.i(TAG, "Launched navigation to $destination")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch navigation: ${e.message}", e)
        }
    }

    private fun handleDismiss(skillId: String, actionLogId: Long) {
        // Update the action log with user dismissal
        GlobalScope.launch {
            try {
                actionLogRepository.updateWithUserOverride(actionLogId, UserOverride.DISMISSED.name)
                Log.i(TAG, "[${skillId}] User dismissed action (log id: $actionLogId)")
            } catch (e: Exception) {
                Log.e(TAG, "[${skillId}] Failed to update action log with dismissal", e)
            }
        }
    }

    private fun handleSendMessage(skillId: String, actionLogId: Long, intent: Intent) {
        val messageText = intent.getStringExtra("message_text")
        val contactName = intent.getStringExtra("contact_name") ?: "Unknown"

        Log.i(TAG, "[${skillId}] Message draft approved by user for $contactName: ${messageText?.take(60)}")

        GlobalScope.launch {
            try {
                actionLogRepository.updateWithUserOverride(actionLogId, UserOverride.APPROVED.name)
            } catch (e: Exception) {
                Log.e(TAG, "[${skillId}] Failed to update action log after approval", e)
            }
        }
    }

    private fun handleSendBatteryWarning(skillId: String, actionLogId: Long, intent: Intent) {
        val contactName = intent.getStringExtra("emergency_contact_name") ?: "Emergency Contact"

        Log.i(TAG, "[${skillId}] Battery warning approved by user for $contactName")

        GlobalScope.launch {
            try {
                actionLogRepository.updateWithUserOverride(actionLogId, UserOverride.APPROVED.name)
            } catch (e: Exception) {
                Log.e(TAG, "[${skillId}] Failed to update action log after approval", e)
            }
        }
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"
    }
}
