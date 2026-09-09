package com.snaploop.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.snaploop.app.MainActivity
import com.snaploop.app.R

/** Product FCM entry point. Payloads are treated as wake-up hints, not authority. */
class SnapLoopFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        SnapLoopPushTokenRegistrar.registerBestEffort(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val payload = SnapLoopNotificationContract.parse(
            data = message.data,
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body,
        )
        if (payload.kind == SnapLoopNotificationKind.GENERIC) return

        // Persist only that authenticated state needs a server refresh. The raw
        // invite token/Event ID never enters local preferences.
        SnapLoopPushRefreshStore.markPending(applicationContext)
        SnapLoopNotifications.show(applicationContext, payload)
    }
}

object SnapLoopNotifications {
    const val CHANNEL_INVITES = "snaploop.invites"
    const val CHANNEL_PHOTOS = "snaploop.photos"

    private const val INVITES_NAME = "Event invitations"
    private const val PHOTOS_NAME = "Photo updates"
    private const val INVITES_DESCRIPTION = "Invitations to SnapLoop Events"
    private const val PHOTOS_DESCRIPTION = "Updates when SnapLoop finds new Event photos"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val invitations = NotificationChannel(
            CHANNEL_INVITES,
            INVITES_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = INVITES_DESCRIPTION
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            enableVibration(true)
        }
        val photos = NotificationChannel(
            CHANNEL_PHOTOS,
            PHOTOS_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = PHOTOS_DESCRIPTION
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            enableVibration(true)
        }
        manager.createNotificationChannels(listOf(invitations, photos))
    }

    fun show(context: Context, payload: SnapLoopNotificationPayload) {
        if (payload.kind == SnapLoopNotificationKind.GENERIC) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val pendingIntent = contentIntent(context, payload)
        val channel = when (payload.kind) {
            SnapLoopNotificationKind.INVITE -> CHANNEL_INVITES
            SnapLoopNotificationKind.EVENT_PHOTOS -> CHANNEL_PHOTOS
            SnapLoopNotificationKind.GENERIC -> return
        }
        val extras = Bundle().apply {
            payload.inviteToken?.let { putString(SnapLoopNotificationContract.KEY_INVITE_TOKEN, it) }
            payload.eventId?.let { putString(SnapLoopNotificationContract.KEY_EVENT_ID, it) }
        }

        val builder = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_snaploop)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(Notification.BigTextStyle().bigText(payload.body))
            .setCategory(Notification.CATEGORY_SOCIAL)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setExtras(extras)

        if (payload.kind == SnapLoopNotificationKind.INVITE) {
            builder.addAction(
                Notification.Action.Builder(
                    R.drawable.ic_stat_snaploop,
                    "Open Invite",
                    pendingIntent,
                ).build(),
            )
        }

        manager.notify(SnapLoopNotificationContract.notificationId(payload), builder.build())
    }

    fun cancelInvite(context: Context, token: String) {
        val id = SnapLoopNotificationContract.inviteNotificationId(token) ?: return
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }

    private fun contentIntent(
        context: Context,
        payload: SnapLoopNotificationPayload,
    ): PendingIntent {
        val requestCode = SnapLoopNotificationContract.notificationId(payload)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            payload.inviteToken?.let { putExtra(SnapLoopNotificationContract.KEY_INVITE_TOKEN, it) }
            payload.eventId?.let { putExtra(SnapLoopNotificationContract.KEY_EVENT_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
