package com.bam.sshfs.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bam.sshfs.R
import com.bam.sshfs.data.model.DEFAULT_SSH_PORT
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.net.ConnectionState

/** One host in the connections view: where it is, how it's doing, and the toggle. */
@Composable
fun ConnectionListItem(
    host: Host,
    state: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(host.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = endpoint(host),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = statusLine(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(state),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state is ConnectionState.Connecting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                ToggleButton(state, onConnect, onDisconnect)
            }
        }
    }
}

@Composable
private fun ToggleButton(
    state: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connected = state is ConnectionState.Connected
    TextButton(onClick = if (connected) onDisconnect else onConnect) {
        Text(
            stringResource(
                if (connected) R.string.action_disconnect else R.string.action_connect
            )
        )
    }
}

/** `host` or `host:port` — the default port is left implicit. */
private fun endpoint(host: Host): String =
    if (host.port == DEFAULT_SSH_PORT) host.address else "${host.address}:${host.port}"

@Composable
private fun statusLine(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> stringResource(R.string.connection_state_disconnected)
    ConnectionState.Connecting -> stringResource(R.string.connection_state_connecting)
    is ConnectionState.Connected -> state.serverVersion.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.connection_state_connected_version, it) }
        ?: stringResource(R.string.connection_state_connected)
    is ConnectionState.Failed -> stringResource(R.string.connection_state_failed, state.reason)
}

@Composable
private fun statusColor(state: ConnectionState) = when (state) {
    is ConnectionState.Failed -> MaterialTheme.colorScheme.error
    is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
