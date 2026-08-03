package com.bam.sshfs.ui.identities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bam.sshfs.R
import com.bam.sshfs.data.model.SshKey

/**
 * Create or edit an identity: a name, a username, and at least one credential —
 * a password, a stored key, or both (key first, password as the fallback).
 */
@Composable
fun IdentityEditorDialog(
    initial: IdentityForm,
    keys: List<SshKey>,
    onDismiss: () -> Unit,
    onSave: (IdentityForm) -> Unit,
) {
    var form by remember { mutableStateOf(initial) }
    val isNew = initial.id == 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isNew) R.string.identity_add_title else R.string.identity_edit_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it) },
                    label = { Text(stringResource(R.string.identity_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.username,
                    onValueChange = { form = form.copy(username = it) },
                    label = { Text(stringResource(R.string.identity_field_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(form) { form = it }
                KeyPicker(form, keys) { form = it }
                if (form.validate() == IdentityFormError.NO_CREDENTIAL) {
                    Text(
                        text = stringResource(R.string.identity_error_no_credential),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = form.validate() == null, onClick = { onSave(form) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The password field. On an identity that already has one, the stored secret is
 * never shown: the field stays empty until the user types a replacement, and a
 * **Remove** action clears it instead.
 */
@Composable
private fun PasswordField(form: IdentityForm, onChange: (IdentityForm) -> Unit) {
    val keeping = form.password == null && form.hadPassword
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = form.password.orEmpty(),
            onValueChange = { onChange(form.copy(password = it)) },
            label = { Text(stringResource(R.string.identity_field_password)) },
            supportingText = {
                Text(
                    stringResource(
                        when {
                            keeping -> R.string.identity_password_stored
                            form.hadPassword && form.password?.isEmpty() == true ->
                                R.string.identity_password_cleared
                            else -> R.string.identity_password_hint
                        },
                    ),
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.willHavePassword && form.hadPassword) {
            TextButton(onClick = { onChange(form.copy(password = "")) }) {
                Text(stringResource(R.string.action_remove_password))
            }
        }
    }
}

/** Pick the key this identity authenticates with, or none. */
@Composable
private fun KeyPicker(form: IdentityForm, keys: List<SshKey>, onChange: (IdentityForm) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = keys.firstOrNull { it.id == form.keyId }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.identity_field_key),
            style = MaterialTheme.typography.labelLarge,
        )
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.name ?: stringResource(R.string.identity_key_none))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.identity_key_none)) },
                    onClick = { open = false; onChange(form.copy(keyId = null)) },
                )
                keys.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(key.name) },
                        onClick = { open = false; onChange(form.copy(keyId = key.id)) },
                    )
                }
            }
        }
        if (keys.isEmpty()) {
            Text(
                text = stringResource(R.string.identity_key_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
