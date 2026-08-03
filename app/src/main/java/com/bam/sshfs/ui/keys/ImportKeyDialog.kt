package com.bam.sshfs.ui.keys

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bam.sshfs.R
import com.bam.sshfs.crypto.KeyImporter

/**
 * Import an existing private key: paste the PEM/OpenSSH block or pick the file
 * with the system picker. The passphrase field appears only once the pasted text
 * actually looks encrypted.
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
    var readError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        }.onSuccess {
            privateKey = it
            readError = null
            if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        }.onFailure { readError = it.message }
    }

    val encrypted = remember(privateKey) { privateKey.isNotBlank() && KeyImporter.isEncrypted(privateKey) }

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
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_pick_key_file)) }
                OutlinedTextField(
                    value = privateKey,
                    onValueChange = { privateKey = it },
                    label = { Text(stringResource(R.string.key_field_private)) },
                    supportingText = { Text(readError ?: stringResource(R.string.key_field_private_hint)) },
                    isError = readError != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 180.dp),
                )
                if (encrypted) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.key_field_passphrase)) },
                        supportingText = { Text(stringResource(R.string.key_field_passphrase_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
