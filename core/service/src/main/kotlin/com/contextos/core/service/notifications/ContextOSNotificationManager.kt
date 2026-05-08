package com.contextos.core.service.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.contextos.core.service.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextOSNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val CHANNEL_ID_INFO = "contextos_info"
        private const val CHANNEL_ID_APPROVAL = "contextos_approval"
        private const val CHANNEL_ID_MESSAGE = "contextos_message"
        private const val CHANNEL_ID_DOCUMENT = "contextos_document"
        private const val CHANNEL_ID_BATTERY = "contextos_battery"

        const val NOTIFICATION_ID_INFO = 1001
        const val NOTIFICATION_ID_APPROVAL = 1002
        const val NOTIFICATION_ID_MESSAGE = 1003
        const val NOTIFICATION_ID_DOCUMENT = 1004
        const val NOTIFICATION_ID_BATTERY = 1005

        private const val REQUEST_CODE_APPROVE = 2001
        private const val REQUEST_CODE_DISMISS = 2002
        private const val REQUEST_CODE_SEND = 2003
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        createChannel(
            manager,
            CHANNEL_ID_INFO,
            "ContextOS Actions",
            "Auto-executed actions taken by ContextOS",
            NotificationManager.IMPORTANCE_LOW,
        )
        createChannel(
            manager,
            CHANNEL_ID_APPROVAL,
            "Pending Approvals",
            "Actions that require your confirmation",
            NotificationManager.IMPORTANCE_HIGH,
        )
        createChannel(
            manager,
            CHANNEL_ID_MESSAGE,
            "Message Drafts",
            "Drafted messages ready to send",
            NotificationManager.IMPORTANCE_HIGH,
        )
        createChannel(
            manager,
            CHANNEL_ID_DOCUMENT,
            "Meeting Documents",
            "Files found for upcoming meetings",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        createChannel(
            manager,
            CHANNEL_ID_BATTERY,
            "Battery Warnings",
            "Low battery alerts",
            NotificationManager.IMPORTANCE_HIGH,
        )
    }

    private fun createChannel(
        manager: NotificationManager,
        id: String,
        name: String,
        description: String,
        importance: Int,
    ) {
        val channel = NotificationChannel(id, name, importance).apply {
            this.description = description
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    // ── Info notification (auto-executed action) ──

    fun buildInfoNotification(
        title: String,
        body: String,
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createOpenAppIntent())
            .build()
    }

    fun showInfoNotification(
        id: Int = NOTIFICATION_ID_INFO,
        title: String,
        body: String,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(id, buildInfoNotification(title, body))
    }

    // ── Approval request notification ──

    fun buildApprovalNotification(
        notificationId: Int,
        title: String,
        body: String,
        skillId: String,
        actionLogId: Long,
        extras: Map<String, String> = emptyMap(),
    ): Notification {
        val approveIntent = createActionIntent(
            action = "ACTION_APPROVE",
            skillId = skillId,
            actionLogId = actionLogId,
            requestCode = REQUEST_CODE_APPROVE + notificationId,
            extras = extras,
        )
        val dismissIntent = createActionIntent(
            action = "ACTION_DISMISS",
            skillId = skillId,
            actionLogId = actionLogId,
            requestCode = REQUEST_CODE_DISMISS + notificationId,
            extras = extras,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_APPROVAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createOpenAppIntent())
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_save,
                "Approve",
                approveIntent,
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                dismissIntent,
            )
            .build()
    }

    fun showApprovalNotification(
        notificationId: Int = NOTIFICATION_ID_APPROVAL,
        title: String,
        body: String,
        skillId: String,
        actionLogId: Long,
        extras: Map<String, String> = emptyMap(),
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            notificationId,
            buildApprovalNotification(notificationId, title, body, skillId, actionLogId, extras),
        )
    }

    // ── Message draft notification ──

    fun buildMessageDraftNotification(
        notificationId: Int,
        title: String,
        body: String,
        draftText: String,
        skillId: String,
        actionLogId: Long,
        extras: Map<String, String> = emptyMap(),
    ): Notification {
        val sendIntent = createActionIntent(
            action = "ACTION_SEND_MESSAGE",
            skillId = skillId,
            actionLogId = actionLogId,
            requestCode = REQUEST_CODE_SEND + notificationId,
            extras = extras,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_MESSAGE)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.BigTextStyle().bigText(draftText))
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createOpenAppIntent())
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Send",
                sendIntent,
            )
            .addAction(
                android.R.drawable.ic_menu_edit,
                "Edit",
                createOpenAppIntent(),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                createActionIntent(
                    action = "ACTION_DISMISS",
                    skillId = skillId,
                    actionLogId = actionLogId,
                    requestCode = REQUEST_CODE_DISMISS + notificationId,
                ),
            )
            .build()
    }

    fun showMessageDraftNotification(
        notificationId: Int = NOTIFICATION_ID_MESSAGE,
        title: String,
        body: String,
        draftText: String,
        skillId: String,
        actionLogId: Long,
        extras: Map<String, String> = emptyMap(),
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            notificationId,
            buildMessageDraftNotification(notificationId, title, body, draftText, skillId, actionLogId, extras),
        )
    }

    // ── Document ready notification ──

    fun buildDocumentNotification(
        notificationId: Int,
        title: String,
        body: String,
        documentLinks: List<Pair<String, String>>, // (title, url)
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_DOCUMENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createOpenAppIntent())
            .setAutoCancel(true)

        documentLinks.take(3).forEach { (docTitle, url) ->
            builder.addAction(
                android.R.drawable.ic_menu_info_details,
                docTitle,
                createOpenUrlIntent(url),
            )
        }

        return builder.build()
    }

    fun showDocumentNotification(
        notificationId: Int = NOTIFICATION_ID_DOCUMENT,
        title: String,
        body: String,
        documentLinks: List<Pair<String, String>>,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            notificationId,
            buildDocumentNotification(notificationId, title, body, documentLinks),
        )
    }

    // ── Battery warning notification ──

    fun buildBatteryWarningNotification(
        notificationId: Int,
        title: String,
        body: String,
        skillId: String,
        actionLogId: Long,
        extras: Map<String, String> = emptyMap(),
    ): Notification {
        val sendIntent = createActionIntent(
            action = "ACTION_SEND_BATTERY_WARNING",
            skillId = skillId,
            actionLogId = actionLogId,
            requestCode = REQUEST_CODE_SEND + notificationId,
            extras = extras,
        )
        val skipIntent = createActionIntent(
            action = "ACTION_DISMISS",
            skillId = skillId,
            actionLogId = actionLogId,
            requestCode = REQUEST_CODE_DISMISS + notificationId,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_BATTERY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createOpenAppIntent())
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Send Warning",
                sendIntent,
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Skip",
                skipIntent,
            )
            .build()
    }

    fun showBatteryWarningNotification(
        notificationId: Int = NOTIFICATION_ID_BATTERY,
        title: String,
        body: String,
        skillId: String,
        actionLogId: Long,
        extras: Map<String, String> = emptyMap(),
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            notificationId,
            buildBatteryWarningNotification(notificationId, title, body, skillId, actionLogId, extras),
        )
    }

    // ── Cancel ──

    fun cancel(notificationId: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(notificationId)
    }

    fun cancelAll() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
    }

    // ── Intent helpers ──

    private fun createOpenAppIntent(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createOpenUrlIntent(url: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            url.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createActionIntent(
        action: String,
        skillId: String,
        actionLogId: Long,
        requestCode: Int,
        extras: Map<String, String> = emptyMap(),
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra("skill_id", skillId)
            putExtra("action_log_id", actionLogId)
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
