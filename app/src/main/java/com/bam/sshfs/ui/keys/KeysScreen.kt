package com.bam.sshfs.ui.keys

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bam.sshfs.R
import com.bam.sshfs.crypto.KeyExport
import com.bam.sshfs.data.model.SshKey

/** Which modal, if any, is on top of the list. */
private sealed interface KeysDialog {
    data object Generate : KeysDialog
    data object Import : KeysDialog
    data class ShowPublic(val key: SshKey) : KeysDialog
    data class ExportPrivate(val key: SshKey) : KeysDialog
    data class Rename(val key: SshKey) : KeysDialog
    data class Delete(val key: SshKey) : KeysDialog
}

/** The Keys screen: every stored pair, plus generate and import. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(modifier: Modifier = Modifier, vm: KeysViewModel = viewModel()) {
    val keys by vm.keys.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val unlink by vm.unlinkPrompt.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val pendingExport by vm.pendingExport.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<KeysDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); vm.dismissError() }
    }
    LaunchedEffect(notice) {
        notice?.let { snackbar.showSnackbar(it); vm.dismissNotice() }
    }

    // The save dialog opens only once the key is unlocked, and a cancelled save
    // drops the decrypted material again rather than leaving it in memory.
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-pem-file"),
    ) { uri: Uri? -> if (uri == null) vm.cancelExport() else vm.writeExport(uri) }
    LaunchedEffect(pendingExport) {
        pendingExport?.let { saver.launch(KeyExport.suggestedFileName(it.key)) }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.keys_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { AddKeyButton { dialog = it } },
    ) { inner ->
        Column(Modifier.padding(inner)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            KeyList(
                keys = keys,
                onShowPublicKey = { dialog = KeysDialog.ShowPublic(it) },
                onExportPrivateKey = { dialog = KeysDialog.ExportPrivate(it) },
                onRename = { dialog = KeysDialog.Rename(it) },
                onDelete = { dialog = KeysDialog.Delete(it) },
            )
        }
    }

    KeysDialogHost(
        dialog = dialog,
        unlink = unlink,
        onDismiss = { dialog = null },
        vm = vm,
    )
}

@Composable
private fun KeysDialogHost(
    dialog: KeysDialog?,
    unlink: UnlinkPrompt?,
    onDismiss: () -> Unit,
    vm: KeysViewModel,
) {
    when (dialog) {
        KeysDialog.Generate -> GenerateKeyDialog(onDismiss) { name, type, comment ->
            vm.generate(name, type, comment); onDismiss()
        }
        KeysDialog.Import -> ImportKeyDialog(onDismiss) { name, key, passphrase, comment ->
            vm.import(name, key, passphrase, comment); onDismiss()
        }
        is KeysDialog.ShowPublic -> PublicKeyDialog(dialog.key, onDismiss)
        is KeysDialog.ExportPrivate -> ExportKeyDialog(dialog.key, onDismiss) {
            vm.prepareExport(dialog.key); onDismiss()
        }
        is KeysDialog.Rename -> RenameKeyDialog(dialog.key, onDismiss) { name ->
            vm.rename(dialog.key, name); onDismiss()
        }
        is KeysDialog.Delete -> DeleteKeyDialog(dialog.key, onDismiss) {
            vm.delete(dialog.key); onDismiss()
        }
        null -> Unit
    }
    unlink?.let { prompt ->
        UnlinkKeyDialog(prompt, vm::dismissUnlinkPrompt) {
            vm.delete(prompt.key, unlink = true)
        }
    }
}

@Composable
private fun KeyList(
    keys: List<SshKey>,
    onShowPublicKey: (SshKey) -> Unit,
    onExportPrivateKey: (SshKey) -> Unit,
    onRename: (SshKey) -> Unit,
    onDelete: (SshKey) -> Unit,
) {
    if (keys.isEmpty()) {
        EmptyKeys()
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(keys, key = { it.id }) { key ->
            KeyListItem(
                key = key,
                onShowPublicKey = { onShowPublicKey(key) },
                onExportPrivateKey = { onExportPrivateKey(key) },
                onRename = { onRename(key) },
                onDelete = { onDelete(key) },
            )
        }
    }
}

@Composable
private fun EmptyKeys() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.keys_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddKeyButton(onPick: (KeysDialog) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ExtendedFloatingActionButton(
            text = { Text(stringResource(R.string.action_add_key)) },
            icon = {},
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_generate)) },
                onClick = { open = false; onPick(KeysDialog.Generate) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_import)) },
                onClick = { open = false; onPick(KeysDialog.Import) },
            )
        }
    }
}
