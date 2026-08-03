package com.bam.sshfs.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bam.sshfs.net.ConnectionRegistry
import com.bam.sshfs.net.ConnectionState
import com.bam.sshfs.ui.connections.ConnectionsScreen
import com.bam.sshfs.ui.hosts.HostsScreen
import com.bam.sshfs.ui.identities.IdentitiesScreen
import com.bam.sshfs.ui.keys.KeysScreen

/**
 * The single-activity shell: a bottom nav bar over the four sections.
 *
 * Each section keeps its own `Scaffold` (top bar, FAB, snackbar); this only owns the
 * nav bar and hands the sections the insets left over. Switching tabs re-composes the
 * section from scratch — the state that matters lives in the database and in
 * [ConnectionRegistry], not in the composables.
 */
@Composable
fun AppShell(modifier: Modifier = Modifier) {
    // Enums are Serializable, so the selected tab survives configuration changes as-is.
    var current by rememberSaveable { mutableStateOf(Destination.Connections) }
    val connected by ConnectionRegistry.states.collectAsStateWithLifecycle()
    val connectedCount = connected.values.count { it is ConnectionState.Connected }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = { current = destination },
                        icon = { TabIcon(destination, connectedCount) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (current) {
                Destination.Connections -> ConnectionsScreen()
                Destination.Hosts -> HostsScreen()
                Destination.Identities -> IdentitiesScreen()
                Destination.Keys -> KeysScreen()
            }
        }
    }
}

/** The Connections tab carries a badge with how many hosts are up. */
@Composable
private fun TabIcon(destination: Destination, connectedCount: Int) {
    val icon = @Composable {
        Icon(destination.icon, contentDescription = stringResource(destination.label))
    }
    if (destination == Destination.Connections && connectedCount > 0) {
        BadgedBox(badge = { Badge { Text(connectedCount.toString()) } }) { icon() }
    } else {
        icon()
    }
}
