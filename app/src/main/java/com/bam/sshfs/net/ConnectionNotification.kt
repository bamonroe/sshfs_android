package com.bam.sshfs.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bam.sshfs.MainActivity
import com.bam.sshfs.R

/**
 * The persistent notification that makes [ConnectionService] a foreground service.
 *
 * Android requires a visible notification for the whole time the sessions are held
 * open, so it may as well be useful: it lists the connected hosts and offers a
 * one-tap disconnect-all, and tapping it opens the connections screen.
 */
object ConnectionNotification {

    const val CHANNEL_ID = "connections"
    const val ID = 1

    /** Create the channel; a no-op after the first call and below API 26. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_connections),
            // Low: this is ambient status, never something to interrupt the user.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_connections_desc)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** The notification for the current [names] of connected hosts. */
    fun build(context: Context, names: List<String>): Notification {
        val text = if (names.isEmpty()) {
            context.getString(R.string.notification_no_connections)
        } else {
            names.joinToString(", ")
        }
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(
                context.resources.getQuantityString(
                    R.plurals.notification_connected_hosts, names.size, names.size,
                )
            )
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .addAction(disconnectAll(context))
            .build()
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun disconnectAll(context: Context): Notification.Action {
        val intent = PendingIntent.getService(
            context,
            1,
            Intent(context, ConnectionService::class.java)
                .setAction(ConnectionService.ACTION_DISCONNECT_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            null,
            context.getString(R.string.notification_disconnect_all),
            intent,
        ).build()
    }
}
