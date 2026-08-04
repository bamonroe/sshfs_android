package com.bam.sshfs.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bam.sshfs.R
import com.bam.sshfs.backup.BackupCrypto
import com.bam.sshfs.backup.BackupExporter
import com.bam.sshfs.backup.BackupJson
import com.bam.sshfs.backup.BackupRestorer
import com.bam.sshfs.crypto.SecretStoreException
import com.bam.sshfs.crypto.Secrets
import com.bam.sshfs.data.db.SshfsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which passphrase the dialog is asking for. */
sealed interface BackupPrompt {
    /** Choose a passphrase to seal a new backup with. */
    data object Export : BackupPrompt

    /** Enter the passphrase that opens [uri]. */
    data class Import(val uri: Uri) : BackupPrompt
}

/**
 * Drives the encrypted whole-configuration backup and its restore.
 *
 * The export is deliberately two-step: the document is unsealed and re-sealed under
 * the user's passphrase *before* the save dialog opens, so a cancelled save leaves
 * nothing on disk, and the only plaintext copy lives in this object's memory for the
 * moment between the two.
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val db = SshfsDatabase.get(app)
    private val secrets = Secrets.store(app)
    private val exporter = BackupExporter(db.keyDao(), db.identityDao(), db.hostDao(), secrets)
    private val restorer = BackupRestorer(db.keyDao(), db.identityDao(), db.hostDao(), secrets)

    private val _prompt = MutableStateFlow<BackupPrompt?>(null)

    /** Non-null while the passphrase dialog is up. */
    val prompt: StateFlow<BackupPrompt?> = _prompt.asStateFlow()

    private val _pendingFile = MutableStateFlow<String?>(null)

    /** A sealed backup waiting for the user to pick a destination. */
    val pendingFile: StateFlow<String?> = _pendingFile.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)

    /** A one-shot line for the snackbar: what happened, or why it didn't. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismissMessage() { _message.value = null }

    fun startExport() { _prompt.value = BackupPrompt.Export }

    fun startImport(uri: Uri) { _prompt.value = BackupPrompt.Import(uri) }

    fun cancelPrompt() { _prompt.value = null }

    /** Drop a sealed backup the user decided not to save after all. */
    fun cancelSave() { _pendingFile.value = null }

    /** Run the pending operation with the passphrase the user typed. */
    fun submitPassphrase(passphrase: String) {
        when (val prompt = _prompt.value) {
            null -> Unit
            BackupPrompt.Export -> work { seal(passphrase) }
            is BackupPrompt.Import -> work { restore(prompt.uri, passphrase) }
        }
        _prompt.value = null
    }

    /** Write the sealed backup to the document the user picked, then forget it. */
    fun writeBackup(uri: Uri) = work {
        val text = _pendingFile.value ?: return@work
        val app = getApplication<Application>()
        // "wt" truncates: the picker hands back existing documents, and a shorter
        // backup written over a longer one would leave a tail of the old file behind.
        withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            } ?: throw SecretStoreException("Could not open the chosen file for writing")
        }
        _pendingFile.value = null
        _message.value = app.getString(R.string.backup_export_done)
    }

    private suspend fun seal(passphrase: String) {
        val app = getApplication<Application>()
        Secrets.unlockForRead(
            app.getString(R.string.auth_prompt_title),
            app.getString(R.string.auth_prompt_backup),
            *exporter.sealedSecrets().toTypedArray(),
        )
        val now = System.currentTimeMillis()
        // Both the unseal-per-row and 210k PBKDF2 rounds are far too slow for the
        // main thread.
        _pendingFile.value = withContext(Dispatchers.IO) {
            BackupCrypto.seal(BackupJson.encode(exporter.collect(now)), passphrase)
        }
    }

    private suspend fun restore(uri: Uri, passphrase: String) {
        val app = getApplication<Application>()
        val text = withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw SecretStoreException("Could not open the chosen backup file")
        }
        val document = withContext(Dispatchers.Default) {
            BackupJson.decode(BackupCrypto.open(text, passphrase))
        }
        // Re-sealing under a gated Keystore key needs the user authenticated to *write*.
        Secrets.unlockForWrite(
            app,
            app.getString(R.string.auth_prompt_title),
            app.getString(R.string.auth_prompt_restore),
        )
        val result = withContext(Dispatchers.IO) { restorer.restore(document) }
        _message.value = app.getString(
            R.string.backup_restore_done,
            result.keys,
            result.identities,
            result.hosts,
        )
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: Exception) {
                _message.value = e.message ?: e.javaClass.simpleName
            } finally {
                _busy.value = false
            }
        }
    }
}
