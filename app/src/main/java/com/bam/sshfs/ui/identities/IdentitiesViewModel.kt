package com.bam.sshfs.ui.identities

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bam.sshfs.R
import com.bam.sshfs.crypto.PassthroughSecretStore
import com.bam.sshfs.crypto.SecretStore
import com.bam.sshfs.data.db.SshfsDatabase
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.SshKey
import com.bam.sshfs.data.repo.IdentityRepository
import com.bam.sshfs.data.repo.KeyRepository
import com.bam.sshfs.data.repo.ReferencedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A delete that needs the user to confirm unlinking the hosts defaulting to the identity. */
data class UnlinkIdentityPrompt(val identity: Identity, val references: Int)

/** Drives the Identities screen: the stored list, the key picker, and CRUD. */
class IdentitiesViewModel(
    app: Application,
    private val repo: IdentityRepository,
    private val keyRepo: KeyRepository,
    private val secrets: SecretStore,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(
        app,
        SshfsDatabase.get(app).let { IdentityRepository(it.identityDao(), it.hostDao()) },
        SshfsDatabase.get(app).let { KeyRepository(it.keyDao(), it.identityDao()) },
        PassthroughSecretStore(),
    )

    val identities: StateFlow<List<Identity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every stored key, for the editor's "authenticate with" picker. */
    val keys: StateFlow<List<SshKey>> = keyRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _unlinkPrompt = MutableStateFlow<UnlinkIdentityPrompt?>(null)
    val unlinkPrompt: StateFlow<UnlinkIdentityPrompt?> = _unlinkPrompt.asStateFlow()

    fun dismissError() { _error.value = null }

    fun dismissUnlinkPrompt() { _unlinkPrompt.value = null }

    /** Create or update the identity described by [form], encrypting a new password. */
    fun save(form: IdentityForm) = work {
        form.validate()?.let { _error.value = message(it); return@work }
        val existing = if (form.id == 0L) null else repo.byId(form.id)
        repo.save(
            Identity(
                id = form.id,
                name = form.name.trim(),
                username = form.username.trim(),
                passwordCiphertext = passwordFor(form, existing),
                keyId = form.keyId,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
    }

    /** Delete, surfacing an [UnlinkIdentityPrompt] instead of failing when hosts use it. */
    fun delete(identity: Identity, unlink: Boolean = false) = work {
        try {
            repo.delete(identity, unlink)
            _unlinkPrompt.value = null
        } catch (e: ReferencedException) {
            _unlinkPrompt.value = UnlinkIdentityPrompt(identity, e.referenceCount)
        }
    }

    /** Keep, clear, or replace the stored password according to the draft's intent. */
    private fun passwordFor(form: IdentityForm, existing: Identity?): String? = when {
        form.password == null -> existing?.passwordCiphertext
        form.password.isBlank() -> null
        else -> secrets.encrypt(form.password)
    }

    private fun message(error: IdentityFormError): String = getApplication<Application>().getString(
        when (error) {
            IdentityFormError.BLANK_NAME -> R.string.identity_error_name
            IdentityFormError.BLANK_USERNAME -> R.string.identity_error_username
            IdentityFormError.NO_CREDENTIAL -> R.string.identity_error_no_credential
        },
    )

    /** Run [block] with the busy flag set, funnelling failures into [error]. */
    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: Exception) {
                _error.value = e.message ?: e.javaClass.simpleName
            } finally {
                _busy.value = false
            }
        }
    }
}
