package com.bam.sshfs.ui.keys

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
 * The "give me a private key" half of a dialog: pick a file or paste the block, with
 * a passphrase field that appears only once the text actually looks encrypted.
 *
 * Shared by [ImportKeyDialog] (a brand new key) and [SupplyKeyDialog] (the private
 * half of a placeholder restored from a config-only export), so the two behave
 * identically — file picking, encryption detection and all.
 */
@Composable
fun PrivateKeyField(
    privateKey: String,
    onPrivateKeyChange: (String) -> Unit,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    encrypted: Boolean,
    modifier: Modifier = Modifier,
    onFileName: (String) -> Unit = {},
) {
    var readError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        }.onSuccess {
            onPrivateKeyChange(it)
            readError = null
            onFileName(uri.lastPathSegment?.substringAfterLast('/').orEmpty())
        }.onFailure { readError = it.message }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_pick_key_file)) }
        OutlinedTextField(
            value = privateKey,
            onValueChange = { onPrivateKeyChange(it); readError = null },
            label = { Text(stringResource(R.string.key_field_private)) },
            supportingText = { Text(readError ?: stringResource(R.string.key_field_private_hint)) },
            isError = readError != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 180.dp),
        )
        if (encrypted) {
            OutlinedTextField(
                value = passphrase,
                onValueChange = onPassphraseChange,
                label = { Text(stringResource(R.string.key_field_passphrase)) },
                supportingText = { Text(stringResource(R.string.key_field_passphrase_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** True when [privateKey] is non-blank and its block says it is passphrase-protected. */
@Composable
fun rememberEncrypted(privateKey: String): Boolean =
    remember(privateKey) { privateKey.isNotBlank() && KeyImporter.isEncrypted(privateKey) }
