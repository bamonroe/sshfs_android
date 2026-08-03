package com.bam.sshfs.ui.identities

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bam.sshfs.R
import com.bam.sshfs.data.model.Identity

/** Confirm deleting an identity that no host defaults to. */
@Composable
fun DeleteIdentityDialog(identity: Identity, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.identity_delete_title)) },
        text = { Text(stringResource(R.string.identity_delete_body, identity.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Confirm deleting an identity that hosts still default to, unlinking them first. */
@Composable
fun UnlinkIdentityDialog(
    prompt: UnlinkIdentityPrompt,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.identity_in_use_title)) },
        text = {
            Text(stringResource(R.string.identity_in_use_body, prompt.identity.name, prompt.references))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_unlink_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
