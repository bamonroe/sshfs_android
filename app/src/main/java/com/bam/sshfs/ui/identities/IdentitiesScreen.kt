package com.bam.sshfs.ui.identities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.SshKey

/** Which modal, if any, is on top of the list. */
private sealed interface IdentitiesDialog {
    data class Edit(val form: IdentityForm) : IdentitiesDialog
    data class Delete(val identity: Identity) : IdentitiesDialog
}

/** The Identities screen: every stored identity, plus add / edit / delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentitiesScreen(modifier: Modifier = Modifier, vm: IdentitiesViewModel = viewModel()) {
    val identities by vm.identities.collectAsStateWithLifecycle()
    val keys by vm.keys.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val unlink by vm.unlinkPrompt.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<IdentitiesDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); vm.dismissError() }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.identities_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.action_add_identity)) },
                icon = {},
                onClick = { dialog = IdentitiesDialog.Edit(IdentityForm()) },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            IdentityList(
                identities = identities,
                keys = keys,
                onEdit = { dialog = IdentitiesDialog.Edit(IdentityForm.of(it)) },
                onDelete = { dialog = IdentitiesDialog.Delete(it) },
            )
        }
    }

    IdentitiesDialogHost(
        dialog = dialog,
        keys = keys,
        unlink = unlink,
        onDismiss = { dialog = null },
        vm = vm,
    )
}

@Composable
private fun IdentitiesDialogHost(
    dialog: IdentitiesDialog?,
    keys: List<SshKey>,
    unlink: UnlinkIdentityPrompt?,
    onDismiss: () -> Unit,
    vm: IdentitiesViewModel,
) {
    when (dialog) {
        is IdentitiesDialog.Edit -> IdentityEditorDialog(dialog.form, keys, onDismiss) { form ->
            vm.save(form); onDismiss()
        }
        is IdentitiesDialog.Delete -> DeleteIdentityDialog(dialog.identity, onDismiss) {
            vm.delete(dialog.identity); onDismiss()
        }
        null -> Unit
    }
    unlink?.let { prompt ->
        UnlinkIdentityDialog(prompt, vm::dismissUnlinkPrompt) {
            vm.delete(prompt.identity, unlink = true)
        }
    }
}

@Composable
private fun IdentityList(
    identities: List<Identity>,
    keys: List<SshKey>,
    onEdit: (Identity) -> Unit,
    onDelete: (Identity) -> Unit,
) {
    if (identities.isEmpty()) {
        EmptyIdentities()
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(identities, key = { it.id }) { identity ->
            IdentityListItem(
                identity = identity,
                keyName = keyNameFor(identity, keys),
                onEdit = { onEdit(identity) },
                onDelete = { onDelete(identity) },
            )
        }
    }
}

@Composable
private fun EmptyIdentities() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.identities_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
