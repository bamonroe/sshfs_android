package com.bam.sshfs.ui.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.bam.sshfs.R
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.net.ExtraArgs

/**
 * Create or edit a host: where the server is, which identity to sign in with by
 * default, and any extra ssh options. The **Test connection** action dials the
 * draft without saving it.
 */
@Composable
fun HostEditorDialog(
    initial: HostForm,
    identities: List<Identity>,
    test: TestState,
    onTest: (HostForm) -> Unit,
    onDismiss: () -> Unit,
    onSave: (HostForm) -> Unit,
) {
    var form by remember { mutableStateOf(initial) }
    val isNew = initial.id == 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.host_add_title else R.string.host_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it) },
                    label = { Text(stringResource(R.string.host_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AddressAndPort(form) { form = it }
                IdentityPicker(form, identities) { form = it }
                OutlinedTextField(
                    value = form.remoteRoot,
                    onValueChange = { form = form.copy(remoteRoot = it) },
                    label = { Text(stringResource(R.string.host_field_remote_root)) },
                    supportingText = { Text(stringResource(R.string.host_field_remote_root_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExtraArgsField(form) { form = it }
                TestConnectionRow(form, test, onTest)
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

/** Address beside the port, since the port is almost always left at its default. */
@Composable
private fun AddressAndPort(form: HostForm, onChange: (HostForm) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = form.address,
            onValueChange = { onChange(form.copy(address = it)) },
            label = { Text(stringResource(R.string.host_field_address)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = form.port,
            onValueChange = { onChange(form.copy(port = it.filter(Char::isDigit))) },
            label = { Text(stringResource(R.string.host_field_port)) },
            placeholder = { Text(stringResource(R.string.host_port_default)) },
            isError = form.validate() == HostFormError.BAD_PORT,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier.width(110.dp),
        )
    }
}

/** Pick the identity this host signs in with by default, or none. */
@Composable
private fun IdentityPicker(
    form: HostForm,
    identities: List<Identity>,
    onChange: (HostForm) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = identities.firstOrNull { it.id == form.defaultIdentityId }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.host_field_identity),
            style = MaterialTheme.typography.labelLarge,
        )
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.name ?: stringResource(R.string.host_identity_none))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.host_identity_none)) },
                    onClick = { open = false; onChange(form.copy(defaultIdentityId = null)) },
                )
                identities.forEach { identity ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.host_identity_entry, identity.name, identity.username)) },
                        onClick = { open = false; onChange(form.copy(defaultIdentityId = identity.id)) },
                    )
                }
            }
        }
        if (identities.isEmpty()) {
            Text(
                text = stringResource(R.string.host_identity_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The multi-line `ssh_config`-style options field, with per-line complaints. */
@Composable
private fun ExtraArgsField(form: HostForm, onChange: (HostForm) -> Unit) {
    val problems = ExtraArgs.problems(form.extraArgs)
    OutlinedTextField(
        value = form.extraArgs,
        onValueChange = { onChange(form.copy(extraArgs = it)) },
        label = { Text(stringResource(R.string.host_field_extra_args)) },
        supportingText = {
            Text(
                text = problems.firstOrNull()
                    ?.let { stringResource(R.string.host_extra_args_bad_line, it.line, it.text) }
                    ?: stringResource(R.string.host_field_extra_args_hint),
                color = if (problems.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        },
        isError = problems.isNotEmpty(),
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The Test connection button and whatever the last attempt reported. */
@Composable
private fun TestConnectionRow(form: HostForm, test: TestState, onTest: (HostForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            enabled = test != TestState.Running && form.validate() == null,
            onClick = { onTest(form) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (test == TestState.Running) R.string.host_test_running else R.string.action_test_connection,
                ),
            )
        }
        (test as? TestState.Done)?.let { done ->
            Text(
                text = done.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (done.success) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}
