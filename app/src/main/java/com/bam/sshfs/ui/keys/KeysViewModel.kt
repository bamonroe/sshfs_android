package com.bam.sshfs.ui.keys

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bam.sshfs.R
import com.bam.sshfs.crypto.KeyExport
import com.bam.sshfs.crypto.KeyImporter
import com.bam.sshfs.crypto.KeyMaterial
import com.bam.sshfs.crypto.KeyMaterialException
import com.bam.sshfs.crypto.KeyPairFactory
import com.bam.sshfs.crypto.SecretStore
import com.bam.sshfs.crypto.SecretStoreException
import com.bam.sshfs.crypto.Secrets
import com.bam.sshfs.data.db.SshfsDatabase
import com.bam.sshfs.data.model.KeyOrigin
import com.bam.sshfs.data.model.KeyType
import com.bam.sshfs.data.model.SshKey
import com.bam.sshfs.data.repo.KeyRepository
import com.bam.sshfs.data.repo.ReferencedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A delete that needs the user to confirm unlinking the identities using the key. */
data class UnlinkPrompt(val key: SshKey, val references: Int)

/**
 * A key whose private material has been unlocked and is waiting for the user to
 * choose where to save it. Held only until the save dialog returns.
 */
data class PendingExport(val key: SshKey, val privateKey: String)

/** Drives the Keys screen: the stored list plus generate / import / delete. */
class KeysViewModel(
    app: Application,
    private val repo: KeyRepository,
    private val secrets: SecretStore,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(
        app,
        SshfsDatabase.get(app).let { KeyRepository(it.keyDao(), it.identityDao()) },
        Secrets.store(app),
    )

    val keys: StateFlow<List<SshKey>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _unlinkPrompt = MutableStateFlow<UnlinkPrompt?>(null)
    val unlinkPrompt: StateFlow<UnlinkPrompt?> = _unlinkPrompt.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _pendingExport = MutableStateFlow<PendingExport?>(null)
    val pendingExport: StateFlow<PendingExport?> = _pendingExport.asStateFlow()

    fun dismissError() { _error.value = null }

    fun dismissNotice() { _notice.value = null }

    fun dismissUnlinkPrompt() { _unlinkPrompt.value = null }

    /** Generate a pair on-device and store it. Key generation is CPU-bound: off-main. */
    fun generate(name: String, type: KeyType, comment: String) = work {
        val material = withContext(Dispatchers.Default) {
            KeyPairFactory.generate(type, comment.ifBlank { name })
        }
        store(name, material, KeyOrigin.GENERATED, passphrase = null)
    }

    /** Import a pasted or picked private key, decrypting with [passphrase] if given. */
    fun import(name: String, privateKey: String, passphrase: String?, comment: String) = work {
        val material = withContext(Dispatchers.Default) {
            KeyImporter.load(privateKey, passphrase?.ifBlank { null }, comment.ifBlank { name })
        }
        store(name, material, KeyOrigin.IMPORTED, passphrase?.ifBlank { null })
    }

    /**
     * Fill in the private half of a placeholder key restored from a config-only file.
     *
     * The row keeps its id, name and every identity that links to it — only the
     * material and the fields derived from it change, which is the whole point: the
     * configuration was restored first and the secret catches up afterwards.
     */
    fun supplyPrivateKey(key: SshKey, privateKey: String, passphrase: String?) = work {
        val app = getApplication<Application>()
        val material = withContext(Dispatchers.Default) {
            KeyImporter.load(privateKey, passphrase?.ifBlank { null }, key.name)
        }
        Secrets.unlockForWrite(
            app,
            app.getString(R.string.auth_prompt_title),
            app.getString(R.string.auth_prompt_save_key),
        )
        val sealed = withContext(Dispatchers.IO) {
            secrets.encrypt(material.privateKey) to passphrase?.ifBlank { null }?.let(secrets::encrypt)
        }
        repo.save(
            key.copy(
                type = material.type,
                privateKeyCiphertext = sealed.first,
                // The placeholder's public half came from the export; the supplied
                // private key is authoritative now, and the two must agree.
                publicKey = material.publicKey,
                hasPassphrase = material.encrypted,
                passphraseCiphertext = sealed.second,
            ),
        )
        _notice.value = app.getString(R.string.key_supplied, key.name)
    }

    /** Rename an existing key; nothing else about a stored pair is editable. */
    fun rename(key: SshKey, name: String) = work { repo.save(key.copy(name = name.trim())) }

    /** Delete, surfacing an [UnlinkPrompt] instead of failing when identities use it. */
    fun delete(key: SshKey, unlink: Boolean = false) = work {
        try {
            repo.delete(key, unlink)
            _unlinkPrompt.value = null
        } catch (e: ReferencedException) {
            _unlinkPrompt.value = UnlinkPrompt(key, e.referenceCount)
        }
    }

    /**
     * Unlock and decrypt one key's private material, ready for a save dialog.
     *
     * The prompt happens here, before the file picker opens, so the user confirms
     * with their fingerprint or PIN and only then chooses a destination.
     */
    fun prepareExport(key: SshKey) = work {
        val app = getApplication<Application>()
        if (!key.hasPrivateHalf) {
            _error.value = app.getString(R.string.key_placeholder_no_export)
            return@work
        }
        Secrets.unlockForRead(
            app.getString(R.string.auth_prompt_title),
            app.getString(R.string.auth_prompt_export_key),
            key.privateKeyCiphertext,
        )
        val privateKey = withContext(Dispatchers.IO) { secrets.decrypt(key.privateKeyCiphertext) }
        _pendingExport.value = PendingExport(key, privateKey)
    }

    /** Drop unlocked material the user decided not to save after all. */
    fun cancelExport() { _pendingExport.value = null }

    /** Write the pending export to the document the user picked, then forget it. */
    fun writeExport(uri: Uri) = work {
        val pending = _pendingExport.value ?: return@work
        val app = getApplication<Application>()
        // "wt" truncates: the picker will happily hand back an existing document, and a
        // shorter key written over a longer one would otherwise leave a tail behind.
        withContext(Dispatchers.IO) {
            val bytes = KeyExport.fileContents(pending.privateKey).toByteArray(Charsets.UTF_8)
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: throw SecretStoreException("Could not open the chosen file for writing")
        }
        _pendingExport.value = null
        _notice.value = app.getString(R.string.key_export_done, pending.key.name)
    }

    private suspend fun store(
        name: String,
        material: KeyMaterial,
        origin: KeyOrigin,
        passphrase: String?,
    ) {
        val app = getApplication<Application>()
        // With the gate on, sealing needs the user authenticated too — ask once, here,
        // rather than letting the Keystore refuse the write half-way through.
        Secrets.unlockForWrite(
            app,
            app.getString(R.string.auth_prompt_title),
            app.getString(R.string.auth_prompt_save_key),
        )
        // Keystore calls are IPC to keystore2 and must not sit on the main thread.
        val sealed = withContext(Dispatchers.IO) {
            secrets.encrypt(material.privateKey) to passphrase?.let { secrets.encrypt(it) }
        }
        repo.save(
            SshKey(
                name = name.trim(),
                type = material.type,
                privateKeyCiphertext = sealed.first,
                publicKey = material.publicKey,
                hasPassphrase = material.encrypted,
                passphraseCiphertext = sealed.second,
                origin = origin,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Run [block] with the busy flag set, funnelling failures into [error]. */
    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: KeyMaterialException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: e.javaClass.simpleName
            } finally {
                _busy.value = false
            }
        }
    }
}
