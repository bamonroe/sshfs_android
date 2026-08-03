package com.bam.sshfs.net

import android.content.Context
import com.bam.sshfs.crypto.KeystoreSecretStore
import com.bam.sshfs.data.db.SshfsDatabase
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.net.ssh.CredentialResolver
import com.bam.sshfs.net.ssh.FileKnownHostsStore
import com.bam.sshfs.net.ssh.ReconnectingSession
import com.bam.sshfs.net.ssh.SftpSession
import com.bam.sshfs.net.ssh.SshConnector
import com.bam.sshfs.provider.RemoteWorkers
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The live SSH sessions, one per connected host.
 *
 * Process-wide and outside any ViewModel: the foreground [ConnectionService] keeps
 * the process alive, the connections screen reads state through
 * [ConnectionRegistry], and the `DocumentsProvider` will look sessions up here from
 * binder threads. The service owns the *lifetime*, this object owns the *sessions*,
 * so a rotated activity never drops a working connection.
 *
 * Sessions are [ReconnectingSession]s: a dropped link is re-dialled underneath the
 * provider instead of surfacing as a root that vanished mid-browse.
 */
class ConnectionManager private constructor(context: Context) {

    /**
     * One connected host as the provider sees it.
     *
     * [rootPath] is resolved once, at connect time, because `Host.remoteRoot` may be
     * `.` or `~/…` and canonicalizing it costs a round trip — `queryRoots` is called
     * often and must not pay that.
     */
    data class ConnectedHost(val host: Host, val rootPath: String, val session: SftpSession)

    private val app = context.applicationContext
    private val db = SshfsDatabase.get(app)
    private val credentials = CredentialResolver(KeystoreSecretStore())
    private val connector = SshConnector(FileKnownHostsStore(File(app.filesDir, KNOWN_HOSTS)))

    private val lock = Any()
    private val live = linkedMapOf<Long, ConnectedHost>()

    /** The session for [hostId], or null when that host isn't connected. */
    fun sessionOf(hostId: Long): SftpSession? = synchronized(lock) { live[hostId]?.session }

    /** Everything the `DocumentsProvider` needs about [hostId], or null if it is down. */
    fun connected(hostId: Long): ConnectedHost? = synchronized(lock) { live[hostId] }

    /** Every connected host, in connection order — one SAF root each. */
    fun connected(): List<ConnectedHost> = synchronized(lock) { live.values.toList() }

    /** Names of the connected hosts, connection order — the notification's text. */
    fun connectedNames(): List<String> = synchronized(lock) { live.values.map { it.host.name } }

    fun isEmpty(): Boolean = synchronized(lock) { live.isEmpty() }

    /**
     * Authenticate to [host] and keep the session; blocking work runs on IO.
     *
     * A failure lands in the registry as [ConnectionState.Failed] rather than
     * throwing: this runs from a service command, with no caller to catch it.
     */
    suspend fun connect(host: Host) {
        if (sessionOf(host.id) != null) return
        ConnectionRegistry.set(host.id, ConnectionState.Connecting)
        ConnectionRegistry.set(host.id, withContext(Dispatchers.IO) { open(host) })
        SafRoots.notifyChanged(app)
    }

    /** Close [hostId]'s session and drop it out of the roots list. */
    fun disconnect(hostId: Long) {
        val closing = synchronized(lock) { live.remove(hostId) }
        closing?.let { runCatching { it.session.close() } }
        RemoteWorkers.release(hostId)
        ConnectionRegistry.clear(hostId)
        SafRoots.notifyChanged(app)
    }

    /** Close everything — the service stopping, or the user disconnecting all. */
    fun disconnectAll() {
        val closing = synchronized(lock) { live.toMap().also { live.clear() } }
        closing.forEach { (id, it) ->
            runCatching { it.session.close() }
            RemoteWorkers.release(id)
            ConnectionRegistry.clear(id)
        }
        SafRoots.notifyChanged(app)
    }

    /** Dial [host], returning the state the registry should show. */
    private suspend fun open(host: Host): ConnectionState = try {
        val identity = host.defaultIdentityId?.let { db.identityDao().byId(it) }
        if (identity == null) {
            ConnectionState.Failed("No identity is set for this host.")
        } else {
            val key = identity.keyId?.let { db.keyDao().byId(it) }
            // Credentials are resolved per re-dial, so a rotated password takes
            // effect on the next reconnect without a fresh handshake here.
            val session = ReconnectingSession {
                connector.connect(host, credentials.resolve(identity, key))
            }
            // Touching the session forces the first handshake, so a bad credential
            // fails here rather than on the picker's first directory listing.
            val state = ConnectionState.Connected(session.serverVersion, session.fingerprint)
            val rootPath = session.canonicalize(host.remoteRoot)
            synchronized(lock) { live[host.id] = ConnectedHost(host, rootPath, session) }
            state
        }
    } catch (e: Exception) {
        ConnectionState.Failed(e.message ?: e.javaClass.simpleName)
    }

    companion object {
        private const val KNOWN_HOSTS = "known_hosts"

        @Volatile
        private var instance: ConnectionManager? = null

        fun get(context: Context): ConnectionManager =
            instance ?: synchronized(this) {
                instance ?: ConnectionManager(context).also { instance = it }
            }
    }
}
