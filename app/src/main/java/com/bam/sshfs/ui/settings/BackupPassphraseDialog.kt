package com.bam.sshfs.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bam.sshfs.R

/**
 * Asks for the passphrase that seals or opens a backup.
 *
 * Export asks twice and refuses a mismatch: the passphrase is the *only* way back
 * into the file — nothing on the device can recover it — so a typo would silently
 * produce an unopenable backup.
 */
@Composable
fun BackupPassphraseDialog(
    prompt: BackupPrompt,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val exporting = prompt is BackupPrompt.Export
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = exporting && confirmation.isNotEmpty() && confirmation != passphrase
    val valid = passphrase.isNotEmpty() && (!exporting || confirmation == passphrase)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (exporting) R.string.backup_export_title else R.string.backup_restore_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        if (exporting) R.string.backup_export_body else R.string.backup_restore_body,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.backup_passphrase),
                )
                if (exporting) {
                    PassphraseField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = stringResource(R.string.backup_passphrase_again),
                        isError = mismatch,
                    )
                    if (mismatch) {
                        Text(
                            text = stringResource(R.string.backup_passphrase_mismatch),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(passphrase) }) {
                Text(
                    stringResource(
                        if (exporting) R.string.action_export else R.string.action_restore,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}
