package com.bam.sshfs.ui.hosts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bam.sshfs.R
import com.bam.sshfs.data.model.Host

/** Confirm deleting a host. Nothing references a host, so this is unconditional. */
@Composable
fun DeleteHostDialog(host: Host, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.host_delete_title)) },
        text = { Text(stringResource(R.string.host_delete_body, host.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
