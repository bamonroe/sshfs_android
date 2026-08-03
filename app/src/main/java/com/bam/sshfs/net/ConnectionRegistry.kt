package com.bam.sshfs.net

import com.bam.sshfs.data.model.Host
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where one host stands right now, as the connections view renders it. */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState

    /** The transport is up; [fingerprint] is the server's host key. */
    data class Connected(val serverVersion: String, val fingerprint: String) : ConnectionState

    data class Failed(val reason: String) : ConnectionState
}

/**
 * Process-wide record of which hosts are currently connected.
 *
 * [ConnectionManager] owns the real sessions and is the only writer; this holds just
 * the state the UI renders. Keeping the two apart means the connections screen, the
 * nav-bar badge and the `DocumentsProvider`'s roots all read one map without any of
 * them reaching into the service.
 */
object ConnectionRegistry {

    private val _states = MutableStateFlow<Map<Long, ConnectionState>>(emptyMap())

    /** Host id → state. Hosts absent from the map are [ConnectionState.Disconnected]. */
    val states: StateFlow<Map<Long, ConnectionState>> = _states.asStateFlow()

    fun stateOf(host: Host): ConnectionState =
        _states.value[host.id] ?: ConnectionState.Disconnected

    fun set(hostId: Long, state: ConnectionState) {
        _states.value = _states.value + (hostId to state)
    }

    /** Drop a host back to disconnected, forgetting any recorded failure. */
    fun clear(hostId: Long) {
        _states.value = _states.value - hostId
    }

    /** How many hosts are connected right now — the badge on the nav bar. */
    fun connectedCount(): Int = _states.value.values.count { it is ConnectionState.Connected }
}
