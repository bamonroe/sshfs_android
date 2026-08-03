package com.bam.sshfs.ui.connections

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bam.sshfs.data.db.SshfsDatabase
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.repo.HostRepository
import com.bam.sshfs.net.ConnectionProbe
import com.bam.sshfs.net.ConnectionRegistry
import com.bam.sshfs.net.ConnectionState
import com.bam.sshfs.net.ProbeResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the connections view: every host, its state, and the connect controls. */
class ConnectionsViewModel(
    app: Application,
    private val repo: HostRepository,
    private val probe: ConnectionProbe,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(
        app,
        HostRepository(SshfsDatabase.get(app).hostDao()),
        ConnectionProbe(),
    )

    val hosts: StateFlow<List<Host>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val states: StateFlow<Map<Long, ConnectionState>> = ConnectionRegistry.states

    /**
     * Bring a host up. Until the connection manager exists this is the same
     * unauthenticated handshake the host editor's "Test connection" runs, so a
     * "connected" host here means reachable, not signed in.
     */
    fun connect(host: Host) {
        if (ConnectionRegistry.stateOf(host) is ConnectionState.Connecting) return
        ConnectionRegistry.set(host.id, ConnectionState.Connecting)
        viewModelScope.launch {
            ConnectionRegistry.set(host.id, resultOf(probe.probe(host)))
        }
    }

    fun disconnect(host: Host) = ConnectionRegistry.clear(host.id)

    private fun resultOf(result: ProbeResult): ConnectionState = when (result) {
        is ProbeResult.Reachable ->
            ConnectionState.Connected(result.serverVersion, result.fingerprint)
        is ProbeResult.Failed -> ConnectionState.Failed(result.reason)
    }
}
