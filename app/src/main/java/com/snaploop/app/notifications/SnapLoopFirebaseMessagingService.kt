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
        val route = SnapLoopNotificationContract.route(message.data) ?: return

        // Persist only that authenticated state needs a server refresh. The raw
        // invite token/Event ID never enters local preferences.
        SnapLoopPushRefreshStore.markPending(applicationContext)

        SnapLoopNotifications.show(
            context = applicationContext,
            route = route,
            remoteTitle = message.notification?.title,
            remoteBody = message.notification?.body,
        )
    }
}

object SnapLoopNotifications {
    private const val INVITES_NAME = "Event invitations"
    private const val PHOTOS_NAME = "Photo updates"
    private const val INVITES_DESCRIPTION = "Invitations to SnapLoop Events"
    private const val PHOTOS_DESCRIPTION = "Updates when SnapLoop finds new Event photos"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val invitations = NotificationChannel(
            SnapLoopNotificationContract.CHANNEL_INVITES,
            INVITES_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = INVITES_DESCRIPTION
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            enableVibration(true)
        }
        val photos = NotificationChannel(
            SnapLoopNotificationContract.CHANNEL_PHOTOS,
            PHOTOS_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = PHOTOS_DESCRIPTION
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            enableVibration(true)
        }
        manager.createNotificationChannels(listOf(invitations, photos))
    }

    fun show(
        context: Context,
        route: SnapLoopNotificationContract.Route,
        remoteTitle: String?,
        remoteBody: String?,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val copy = SnapLoopNotificationContract.visibleCopy(route, remoteTitle, remoteBody)
        val pendingIntent = contentIntent(context, route)
        val channel = when (route) {
            is SnapLoopNotificationContract.Route.Invite -> SnapLoopNotificationContract.CHANNEL_INVITES
            is SnapLoopNotificationContract.Route.EventPhotos -> SnapLoopNotificationContract.CHANNEL_PHOTOS
        }

        val builder = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_snaploop)
            .setContentTitle(copy.title)
            .setContentText(copy.body)
            .setStyle(Notification.BigTextStyle().bigText(copy.body))
            .setCategory(Notification.CATEGORY_SOCIAL)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (route is SnapLoopNotificationContract.Route.Invite) {
            builder.addAction(
                Notification.Action.Builder(
                    R.drawable.ic_stat_snaploop,
                    "Open Invite",
                    pendingIntent,
                ).build(),
            )
        }

        manager.notify(SnapLoopNotificationContract.notificationId(route), builder.build())
    }

    fun cancelInvite(context: Context, token: String) {
        val id = SnapLoopNotificationContract.inviteNotificationId(token) ?: return
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }

    private fun contentIntent(
        context: Context,
        route: SnapLoopNotificationContract.Route,
    ): PendingIntent {
        val requestCode = SnapLoopNotificationContract.notificationId(route)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            when (route) {
                is SnapLoopNotificationContract.Route.Invite -> putExtra("inviteToken", route.token)
                is SnapLoopNotificationContract.Route.EventPhotos -> putExtra("eventId", route.eventId)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
