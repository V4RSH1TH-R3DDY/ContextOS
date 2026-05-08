package com.contextos.core.service.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
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
            "ACTION_APPROVE" -> handleApprove(context, skillId, actionLogId)
            "ACTION_DISMISS" -> handleDismiss(context, skillId, actionLogId)
            "ACTION_SEND_MESSAGE" -> handleSendMessage(context, skillId, actionLogId, intent)
            "ACTION_SEND_BATTERY_WARNING" -> handleSendBatteryWarning(context, skillId, actionLogId, intent)
        }

        notificationManager.cancelAll()
    }

    private fun handleApprove(context: Context, skillId: String, actionLogId: Long) {
        // Update the action log with user approval
        GlobalScope.launch {
            try {
                actionLogRepository.updateWithUserOverride(actionLogId, UserOverride.APPROVED.name)
                Log.i(TAG, "[${skillId}] User approved action (log id: $actionLogId)")
            } catch (e: Exception) {
                Log.e(TAG, "[${skillId}] Failed to update action log with approval", e)
            }
        }
    }

    private fun handleDismiss(context: Context, skillId: String, actionLogId: Long) {
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

    private fun handleSendMessage(context: Context, skillId: String, actionLogId: Long, intent: Intent) {
        // Extract message details from intent extras
        val phoneNumber = intent.getStringExtra("phone_number")
        val messageText = intent.getStringExtra("message_text")
        val contactName = intent.getStringExtra("contact_name") ?: "Unknown"

        if (phoneNumber.isNullOrBlank() || messageText.isNullOrBlank()) {
            Log.w(TAG, "[${skillId}] Missing phone number or message text for SMS")
            return
        }

        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager?.sendTextMessage(phoneNumber, null, messageText, null, null)
            Log.i(TAG, "[${skillId}] Sent message to $contactName via SMS")

            // Update the action log with successful send
            GlobalScope.launch {
                try {
                    actionLogRepository.updateWithUserOverride(actionLogId, UserOverride.APPROVED.name)
                } catch (e: Exception) {
                    Log.e(TAG, "[${skillId}] Failed to update action log after SMS send", e)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "[${skillId}] SMS permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "[${skillId}] Failed to send SMS: ${e.message}", e)
        }
    }

    private fun handleSendBatteryWarning(context: Context, skillId: String, actionLogId: Long, intent: Intent) {
        // Extract emergency contact details from intent extras
        val phoneNumber = intent.getStringExtra("emergency_contact_phone")
        val contactName = intent.getStringExtra("emergency_contact_name") ?: "Emergency Contact"
        val batteryLevel = intent.getIntExtra("battery_level", -1)

        if (phoneNumber.isNullOrBlank()) {
            Log.w(TAG, "[${skillId}] No emergency contact phone number configured")
            return
        }

        val message = if (batteryLevel > 0) {
            "Battery warning: Your device battery is at $batteryLevel%. Consider charging it."
        } else {
            "Battery warning: Your device battery is low. Please charge it."
        }

        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager?.sendTextMessage(phoneNumber, null, message, null, null)
            Log.i(TAG, "[${skillId}] Sent battery warning to $contactName")

            // Update the action log with successful send
            GlobalScope.launch {
                try {
                    actionLogRepository.updateWithUserOverride(actionLogId, UserOverride.APPROVED.name)
                } catch (e: Exception) {
                    Log.e(TAG, "[${skillId}] Failed to update action log after warning send", e)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "[${skillId}] SMS permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "[${skillId}] Failed to send battery warning SMS: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"
    }
}
