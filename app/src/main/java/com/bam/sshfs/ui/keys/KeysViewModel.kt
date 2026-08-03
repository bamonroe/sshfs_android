package com.bam.sshfs.ui.keys

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bam.sshfs.crypto.KeyImporter
import com.bam.sshfs.crypto.KeyMaterial
import com.bam.sshfs.crypto.KeyMaterialException
import com.bam.sshfs.crypto.KeyPairFactory
import com.bam.sshfs.crypto.KeystoreSecretStore
import com.bam.sshfs.crypto.SecretStore
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

/** Drives the Keys screen: the stored list plus generate / import / delete. */
class KeysViewModel(
    app: Application,
    private val repo: KeyRepository,
    private val secrets: SecretStore,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(
        app,
        SshfsDatabase.get(app).let { KeyRepository(it.keyDao(), it.identityDao()) },
        KeystoreSecretStore(),
    )

    val keys: StateFlow<List<SshKey>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _unlinkPrompt = MutableStateFlow<UnlinkPrompt?>(null)
    val unlinkPrompt: StateFlow<UnlinkPrompt?> = _unlinkPrompt.asStateFlow()

    fun dismissError() { _error.value = null }

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

    private suspend fun store(
        name: String,
        material: KeyMaterial,
        origin: KeyOrigin,
        passphrase: String?,
    ) {
        repo.save(
            SshKey(
                name = name.trim(),
                type = material.type,
                privateKeyCiphertext = secrets.encrypt(material.privateKey),
                publicKey = material.publicKey,
                hasPassphrase = material.encrypted,
                passphraseCiphertext = passphrase?.let { secrets.encrypt(it) },
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
