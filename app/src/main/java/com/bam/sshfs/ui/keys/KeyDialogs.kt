package com.bam.sshfs.ui.keys

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bam.sshfs.R
import com.bam.sshfs.crypto.OpenSshFormat
import com.bam.sshfs.data.model.SshKey

/**
 * Show the public key in full so the user can copy it into a server's
 * `authorized_keys`. Only the public half is ever displayed — the private key
 * never leaves the database.
 */
@Composable
fun PublicKeyDialog(key: SshKey, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(key.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = OpenSshFormat.fingerprint(key.publicKey)
                        ?: stringResource(R.string.key_fingerprint_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = key.publicKey,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { copyToClipboard(context, key.name, key.publicKey); onDismiss() }) {
                Text(stringResource(R.string.action_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** Rename a stored key — the only editable field on an existing pair. */
@Composable
fun RenameKeyDialog(key: SshKey, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(key.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.key_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.key_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onRename(name) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Confirm deleting a key that nothing references. */
@Composable
fun DeleteKeyDialog(key: SshKey, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.key_delete_title)) },
        text = { Text(stringResource(R.string.key_delete_body, key.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Confirm deleting a key that identities still point at, unlinking them first. */
@Composable
fun UnlinkKeyDialog(prompt: UnlinkPrompt, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.key_in_use_title)) },
        text = {
            Text(stringResource(R.string.key_in_use_body, prompt.key.name, prompt.references))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_unlink_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
