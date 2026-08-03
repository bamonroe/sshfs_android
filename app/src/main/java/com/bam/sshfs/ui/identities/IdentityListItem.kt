package com.bam.sshfs.ui.identities

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
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.SshKey

/** One identity: name, username, and which credentials it carries. */
@Composable
fun IdentityListItem(
    identity: Identity,
    keyName: String?,
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
                Text(identity.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = identity.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = credentials(identity, keyName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IdentityActionsMenu(onEdit, onDelete)
        }
    }
}

/** The credential summary line: password, key, both, or a warning when neither. */
@Composable
private fun credentials(identity: Identity, keyName: String?): String {
    val parts = buildList {
        if (identity.passwordCiphertext != null) add(stringResource(R.string.identity_badge_password))
        keyName?.let { add(stringResource(R.string.identity_badge_key, it)) }
    }
    return if (parts.isEmpty()) stringResource(R.string.identity_badge_none) else parts.joinToString(" · ")
}

/** Resolve an identity's key link to a display name, or null when it has none. */
fun keyNameFor(identity: Identity, keys: List<SshKey>): String? =
    identity.keyId?.let { id -> keys.firstOrNull { it.id == id }?.name }

@Composable
private fun IdentityActionsMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
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
