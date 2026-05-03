package com.contextos.core.service.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationManager: ContextOSNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val skillId = intent.getStringExtra("skill_id") ?: return
        val actionLogId = intent.getLongExtra("action_log_id", -1L)

        when (action) {
            "ACTION_APPROVE" -> handleApprove(context, skillId, actionLogId)
            "ACTION_DISMISS" -> handleDismiss(context, skillId, actionLogId)
            "ACTION_SEND_MESSAGE" -> handleSendMessage(context, skillId, actionLogId)
            "ACTION_SEND_BATTERY_WARNING" -> handleSendBatteryWarning(context, skillId, actionLogId)
        }

        notificationManager.cancelAll()
    }

    private fun handleApprove(context: Context, skillId: String, actionLogId: Long) {
        // TODO: Dispatch approval via ActionDispatcher
    }

    private fun handleDismiss(context: Context, skillId: String, actionLogId: Long) {
        // TODO: Update ActionLog with DISMISSED outcome
    }

    private fun handleSendMessage(context: Context, skillId: String, actionLogId: Long) {
        // TODO: Send drafted message via SmsManager
    }

    private fun handleSendBatteryWarning(context: Context, skillId: String, actionLogId: Long) {
        // TODO: Send battery warning SMS to emergency contact
    }
}
