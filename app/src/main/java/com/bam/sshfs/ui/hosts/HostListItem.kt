package com.bam.sshfs.ui.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bam.sshfs.R
import com.bam.sshfs.data.model.DEFAULT_SSH_PORT
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.net.ExtraArgs

/** One host: name, where it is, and how it signs in. */
@Composable
fun HostListItem(
    host: Host,
    identityName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                    text = summary(host, identityName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HostActionsMenu(onEdit, onDelete)
        }
    }
}

/** `host` or `host:port` — the default port is left implicit. */
private fun endpoint(host: Host): String =
    if (host.port == DEFAULT_SSH_PORT) host.address else "${host.address}:${host.port}"

/** The second line: identity, and a note when extra options are set. */
@Composable
private fun summary(host: Host, identityName: String?): String {
    val parts = buildList {
        add(identityName?.let { stringResource(R.string.host_badge_identity, it) }
            ?: stringResource(R.string.host_badge_no_identity))
        val options = ExtraArgs.parse(host.extraArgs)
        if (options.isNotEmpty()) add(stringResource(R.string.host_badge_options, options.size))
    }
    return parts.joinToString(" · ")
}

/** Resolve a host's default identity link to a display name, or null when it has none. */
fun identityNameFor(host: Host, identities: List<Identity>): String? =
    host.defaultIdentityId?.let { id -> identities.firstOrNull { it.id == id }?.name }

@Composable
private fun HostActionsMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                onClick = { open = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}
