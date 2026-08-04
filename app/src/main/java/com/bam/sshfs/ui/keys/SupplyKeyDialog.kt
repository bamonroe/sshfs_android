package com.bam.sshfs.ui.keys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bam.sshfs.R
import com.bam.sshfs.data.model.SshKey

/**
 * Fill in the private half of a placeholder key — one restored from a config-only
 * export, which carried the public key and the links but no secret.
 *
 * Deliberately not the import dialog: the row already exists, so there is no name and
 * no comment to ask for, only the material itself.
 */
@Composable
fun SupplyKeyDialog(
    key: SshKey,
    onDismiss: () -> Unit,
    onSupply: (privateKey: String, passphrase: String?) -> Unit,
) {
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    val encrypted = rememberEncrypted(privateKey)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.key_supply_title, key.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.key_supply_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PrivateKeyField(
                    privateKey = privateKey,
                    onPrivateKeyChange = { privateKey = it },
                    passphrase = passphrase,
                    onPassphraseChange = { passphrase = it },
                    encrypted = encrypted,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = privateKey.isNotBlank() && (!encrypted || passphrase.isNotEmpty()),
                onClick = { onSupply(privateKey, passphrase.takeIf { encrypted }) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
