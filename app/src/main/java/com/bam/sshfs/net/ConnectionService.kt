package com.bam.sshfs.net

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.bam.sshfs.data.db.SshfsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service holding the SSH sessions open.
 *
 * Its whole job is *lifetime*: a bound activity would die with the UI and a plain
 * background service would be killed within minutes, either of which drops the
 * connection out from under whatever app is browsing the SAF root. The sessions
 * themselves live in [ConnectionManager], so restarting the service doesn't
 * reconnect anything that is already up.
 *
 * Driven by commands rather than binding — [connect], [disconnect] and
 * [disconnectAll] are the only entry points, and the service stops itself once the
 * last host disconnects.
 */
class ConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private val manager by lazy { ConnectionManager.get(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ConnectionNotification.ensureChannel(this)
        // Promote immediately: Android gives a started service only seconds to call
        // startForeground before it throws, and a connect can take much longer.
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connectHost(intent.getLongExtra(EXTRA_HOST_ID, 0L))
            ACTION_DISCONNECT -> {
                manager.disconnect(intent.getLongExtra(EXTRA_HOST_ID, 0L))
                refresh()
            }
            ACTION_DISCONNECT_ALL -> {
                manager.disconnectAll()
                refresh()
            }
        }
        // Sticky would restart us with no sessions and a stale notification; the UI
        // re-issues a connect when the user asks for one.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        manager.disconnectAll()
        super.onDestroy()
    }

    /** Look the host up and dial it, refreshing the notification either way. */
    private fun connectHost(hostId: Long) {
        if (hostId == 0L) return
        scope.launch {
            SshfsDatabase.get(this@ConnectionService).hostDao().byId(hostId)?.let {
                manager.connect(it)
            }
            refresh()
        }
    }

    /** Re-render the notification, and stop once nothing is connected any more. */
    private fun refresh() {
        if (manager.isEmpty()) {
            stopSelf()
            return
        }
        getSystemService(NotificationManager::class.java)
            .notify(ConnectionNotification.ID, ConnectionNotification.build(this, manager.connectedNames()))
    }

    private fun startForeground() {
        val notification = ConnectionNotification.build(this, manager.connectedNames())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ConnectionNotification.ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(ConnectionNotification.ID, notification)
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.bam.sshfs.action.CONNECT"
        const val ACTION_DISCONNECT = "com.bam.sshfs.action.DISCONNECT"
        const val ACTION_DISCONNECT_ALL = "com.bam.sshfs.action.DISCONNECT_ALL"
        const val EXTRA_HOST_ID = "host_id"

        fun connect(context: Context, hostId: Long) =
            start(context, ACTION_CONNECT, hostId)

        fun disconnect(context: Context, hostId: Long) =
            start(context, ACTION_DISCONNECT, hostId)

        fun disconnectAll(context: Context) = start(context, ACTION_DISCONNECT_ALL, null)

        private fun start(context: Context, action: String, hostId: Long?) {
            val intent = Intent(context, ConnectionService::class.java).setAction(action)
            hostId?.let { intent.putExtra(EXTRA_HOST_ID, it) }
            context.startForegroundService(intent)
        }
    }
}
