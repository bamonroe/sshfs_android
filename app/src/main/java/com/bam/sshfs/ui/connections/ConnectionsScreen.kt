package com.bam.sshfs.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bam.sshfs.R
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.net.ConnectionState

/** The connections view: which hosts are up, with connect / disconnect controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(modifier: Modifier = Modifier, vm: ConnectionsViewModel = viewModel()) {
    val hosts by vm.hosts.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.connections_title)) }) },
    ) { inner ->
        Column(Modifier.padding(inner)) {
            ConnectionList(
                hosts = hosts,
                states = states,
                onConnect = vm::connect,
                onDisconnect = vm::disconnect,
            )
        }
    }
}

@Composable
private fun ConnectionList(
    hosts: List<Host>,
    states: Map<Long, ConnectionState>,
    onConnect: (Host) -> Unit,
    onDisconnect: (Host) -> Unit,
) {
    if (hosts.isEmpty()) {
        EmptyState()
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(hosts, key = { it.id }) { host ->
            ConnectionListItem(
                host = host,
                state = states[host.id] ?: ConnectionState.Disconnected,
                onConnect = { onConnect(host) },
                onDisconnect = { onDisconnect(host) },
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.connections_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
