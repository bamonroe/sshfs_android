package com.bam.sshfs.ui.hosts

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
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.model.Identity

/** Which modal, if any, is on top of the list. */
private sealed interface HostsDialog {
    data class Edit(val form: HostForm) : HostsDialog
    data class Delete(val host: Host) : HostsDialog
}

/** The Hosts screen: every stored server, plus add / edit / delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(modifier: Modifier = Modifier, vm: HostsViewModel = viewModel()) {
    val hosts by vm.hosts.collectAsStateWithLifecycle()
    val identities by vm.identities.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HostsDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); vm.dismissError() }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.hosts_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.action_add_host)) },
                icon = {},
                onClick = { vm.resetTest(); dialog = HostsDialog.Edit(HostForm()) },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            HostList(
                hosts = hosts,
                identities = identities,
                onEdit = { vm.resetTest(); dialog = HostsDialog.Edit(HostForm.of(it)) },
                onDelete = { dialog = HostsDialog.Delete(it) },
            )
        }
    }

    HostsDialogHost(
        dialog = dialog,
        identities = identities,
        test = test,
        onDismiss = { dialog = null },
        vm = vm,
    )
}

@Composable
private fun HostsDialogHost(
    dialog: HostsDialog?,
    identities: List<Identity>,
    test: TestState,
    onDismiss: () -> Unit,
    vm: HostsViewModel,
) {
    when (dialog) {
        is HostsDialog.Edit -> HostEditorDialog(
            initial = dialog.form,
            identities = identities,
            test = test,
            onTest = vm::testConnection,
            onDismiss = onDismiss,
            onSave = { form -> vm.save(form); onDismiss() },
        )
        is HostsDialog.Delete -> DeleteHostDialog(dialog.host, onDismiss) {
            vm.delete(dialog.host); onDismiss()
        }
        null -> Unit
    }
}

@Composable
private fun HostList(
    hosts: List<Host>,
    identities: List<Identity>,
    onEdit: (Host) -> Unit,
    onDelete: (Host) -> Unit,
) {
    if (hosts.isEmpty()) {
        EmptyHosts()
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(hosts, key = { it.id }) { host ->
            HostListItem(
                host = host,
                identityName = identityNameFor(host, identities),
                onEdit = { onEdit(host) },
                onDelete = { onDelete(host) },
            )
        }
    }
}

@Composable
private fun EmptyHosts() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.hosts_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
