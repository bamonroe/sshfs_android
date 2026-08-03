package com.bam.sshfs.ui.keys

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
import com.bam.sshfs.crypto.OpenSshFormat
import com.bam.sshfs.data.model.KeyOrigin
import com.bam.sshfs.data.model.SshKey

/** One stored key: name, algorithm, fingerprint, and an actions menu. */
@Composable
fun KeyListItem(
    key: SshKey,
    onShowPublicKey: () -> Unit,
    onExportPrivateKey: () -> Unit,
    onRename: () -> Unit,
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
                Text(key.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle(key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = OpenSshFormat.fingerprint(key.publicKey)
                        ?: stringResource(R.string.key_fingerprint_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KeyActionsMenu(onShowPublicKey, onExportPrivateKey, onRename, onDelete)
        }
    }
}

@Composable
private fun subtitle(key: SshKey): String {
    val origin = stringResource(
        if (key.origin == KeyOrigin.GENERATED) R.string.key_origin_generated
        else R.string.key_origin_imported,
    )
    val locked = if (key.hasPassphrase) " · " + stringResource(R.string.key_passphrase_badge) else ""
    return "${key.type.name} · $origin$locked"
}

@Composable
private fun KeyActionsMenu(
    onShowPublicKey: () -> Unit,
    onExportPrivateKey: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_show_public_key)) },
                onClick = { open = false; onShowPublicKey() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_export_private_key)) },
                onClick = { open = false; onExportPrivateKey() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                onClick = { open = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}
