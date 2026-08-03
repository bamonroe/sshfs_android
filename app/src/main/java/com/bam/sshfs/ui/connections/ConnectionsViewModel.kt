package com.bam.sshfs.ui.connections

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bam.sshfs.data.db.SshfsDatabase
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.repo.HostRepository
import com.bam.sshfs.net.ConnectionRegistry
import com.bam.sshfs.net.ConnectionService
import com.bam.sshfs.net.ConnectionState
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Drives the connections view: every host, its state, and the connect controls. */
class ConnectionsViewModel(
    app: Application,
    private val repo: HostRepository,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, HostRepository(SshfsDatabase.get(app).hostDao()))

    val hosts: StateFlow<List<Host>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val states: StateFlow<Map<Long, ConnectionState>> = ConnectionRegistry.states

    /**
     * Bring a host up.
     *
     * The work happens in [ConnectionService], not here: the session has to outlive
     * this ViewModel, and the state comes back through [ConnectionRegistry] either
     * way, so the UI doesn't care who did the dialling.
     */
    fun connect(host: Host) {
        if (ConnectionRegistry.stateOf(host) is ConnectionState.Connecting) return
        ConnectionService.connect(getApplication(), host.id)
    }

    fun disconnect(host: Host) = ConnectionService.disconnect(getApplication(), host.id)
}
