package com.bam.sshfs.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bam.sshfs.R
import com.bam.sshfs.backup.BackupFile
import java.time.LocalDate

/**
 * The settings view: the biometric / device-credential gate over stored secrets, and
 * the encrypted whole-configuration backup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(),
    backupVm: BackupViewModel = viewModel(),
) {
    val requireAuth by vm.requireAuthentication.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val backupBusy by backupVm.busy.collectAsStateWithLifecycle()
    val backupMessage by backupVm.message.collectAsStateWithLifecycle()
    val prompt by backupVm.prompt.collectAsStateWithLifecycle()
    val pendingFile by backupVm.pendingFile.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }
    LaunchedEffect(backupMessage) {
        backupMessage?.let {
            snackbar.showSnackbar(it)
            backupVm.dismissMessage()
        }
    }

    // The save dialog opens only once the backup is sealed, so a cancelled save leaves
    // nothing behind; the open dialog runs first and the passphrase is asked after.
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFile.MIME_TYPE),
    ) { uri: Uri? -> if (uri == null) backupVm.cancelSave() else backupVm.writeBackup(uri) }
    // A separate launcher because the config-only file is plain JSON and says so; the
    // contract's MIME type is fixed when the launcher is created, not when it fires.
    val configSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFile.CONFIG_MIME_TYPE),
    ) { uri: Uri? -> if (uri == null) backupVm.cancelSave() else backupVm.writeBackup(uri) }
    val opener = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(backupVm::startImport) }
    LaunchedEffect(pendingFile) {
        val today = LocalDate.now().toString()
        when {
            pendingFile == null -> Unit
            pendingFile!!.configOnly -> configSaver.launch(BackupFile.suggestedConfigFileName(today))
            else -> saver.launch(BackupFile.suggestedFileName(today))
        }
    }

    prompt?.let {
        BackupPassphraseDialog(
            prompt = it,
            onDismiss = backupVm::cancelPrompt,
            onConfirm = backupVm::submitPassphrase,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(Modifier.padding(inner).padding(16.dp), Arrangement.spacedBy(8.dp)) {
            SwitchRow(
                title = stringResource(R.string.settings_require_auth),
                // Without a screen lock there is nothing to ask for, and the gated
                // Keystore key can't even be created — say so instead of failing later.
                body = stringResource(
                    if (vm.canAuthenticate) {
                        R.string.settings_require_auth_body
                    } else {
                        R.string.settings_require_auth_unavailable
                    },
                ),
                checked = requireAuth,
                enabled = vm.canAuthenticate && !busy,
                onCheckedChange = vm::setRequireAuthentication,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.settings_backup),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !backupBusy, onClick = backupVm::startExport) {
                    Text(stringResource(R.string.action_back_up))
                }
                OutlinedButton(
                    enabled = !backupBusy,
                    // Backups have no registered MIME type of their own, so the picker
                    // is opened wide rather than filtering everything out.
                    onClick = { opener.launch(arrayOf("*/*")) },
                ) {
                    Text(stringResource(R.string.action_restore))
                }
            }
            Text(
                text = stringResource(R.string.settings_config_backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(enabled = !backupBusy, onClick = backupVm::startConfigExport) {
                Text(stringResource(R.string.action_export_config))
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
