package com.bam.sshfs.ui.keys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bam.sshfs.R
import com.bam.sshfs.crypto.KeyPairFactory
import com.bam.sshfs.data.model.KeyType

/** Name the pair, pick an algorithm, generate it on-device. */
@Composable
fun GenerateKeyDialog(
    onDismiss: () -> Unit,
    onGenerate: (name: String, type: KeyType, comment: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(KeyType.ED25519) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.key_generate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.key_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.key_field_comment)) },
                    supportingText = { Text(stringResource(R.string.key_field_comment_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.key_field_algorithm),
                    style = MaterialTheme.typography.labelLarge,
                )
                KeyPairFactory.GENERATABLE.forEach { option ->
                    AlgorithmOption(option, option == type) { type = option }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onGenerate(name, type, comment) },
            ) { Text(stringResource(R.string.action_generate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AlgorithmOption(type: KeyType, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = when (type) {
                KeyType.ED25519 -> stringResource(R.string.key_algorithm_ed25519)
                KeyType.RSA -> stringResource(R.string.key_algorithm_rsa, KeyPairFactory.RSA_BITS)
                KeyType.ECDSA -> type.name
            },
        )
    }
}
