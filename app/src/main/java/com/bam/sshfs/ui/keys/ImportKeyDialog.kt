package com.bam.sshfs.ui.keys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.bam.sshfs.R

/**
 * Import an existing private key: paste the PEM/OpenSSH block or pick the file
 * with the system picker (see [PrivateKeyField]).
 */
@Composable
fun ImportKeyDialog(
    onDismiss: () -> Unit,
    onImport: (name: String, privateKey: String, passphrase: String?, comment: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    val encrypted = rememberEncrypted(privateKey)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.key_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.key_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrivateKeyField(
                    privateKey = privateKey,
                    onPrivateKeyChange = { privateKey = it },
                    passphrase = passphrase,
                    onPassphraseChange = { passphrase = it },
                    encrypted = encrypted,
                    onFileName = { if (name.isBlank()) name = it },
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.key_field_comment)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && privateKey.isNotBlank() && (!encrypted || passphrase.isNotEmpty()),
                onClick = { onImport(name, privateKey, passphrase.takeIf { encrypted }, comment) },
            ) { Text(stringResource(R.string.action_import)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
