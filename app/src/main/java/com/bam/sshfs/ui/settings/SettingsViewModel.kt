package com.bam.sshfs.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bam.sshfs.R
import com.bam.sshfs.crypto.AuthenticationRequiredException
import com.bam.sshfs.crypto.BiometricAuthGate
import com.bam.sshfs.crypto.SecretAuthGate
import com.bam.sshfs.crypto.SecretResealer
import com.bam.sshfs.data.db.SshfsDatabase
import com.bam.sshfs.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Settings screen — today, just the authentication gate.
 *
 * The toggle is not a plain preference write: flipping it has to re-seal every stored
 * secret under the other scheme, and *both* directions need the user authenticated
 * first (turning it off has to read the gated blobs back). So the switch stays where
 * it was until the whole pass finishes.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = AppSettings.get(app)
    private val resealer = SshfsDatabase.get(app)
        .let { SecretResealer(it.keyDao(), it.identityDao()) }

    val requireAuthentication: StateFlow<Boolean> = settings.requireAuthentication

    /** False on a device with no biometric enrolled and no screen lock — toggle disabled. */
    val canAuthenticate: Boolean = BiometricAuthGate.canAuthenticate(app)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)

    /** A one-shot line for the snackbar: the re-seal outcome, or why it didn't happen. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismissMessage() { _message.value = null }

    /** Turn the gate on or off, re-sealing the stored secrets to match. */
    fun setRequireAuthentication(enabled: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _busy.value = true
            try {
                val subtitle = app.getString(
                    if (enabled) R.string.auth_prompt_enable else R.string.auth_prompt_disable,
                )
                if (!SecretAuthGate.authenticate(
                        app.getString(R.string.auth_prompt_title),
                        subtitle,
                    )
                ) {
                    throw AuthenticationRequiredException(
                        app.getString(R.string.auth_prompt_declined),
                    )
                }
                // Set first, so anything saved during the pass already uses the new scheme.
                settings.setRequireAuthentication(enabled)
                val result = resealer.reseal(enabled)
                _message.value = if (result.failed > 0) {
                    app.getString(R.string.settings_reseal_partial, result.converted, result.failed)
                } else {
                    app.getString(R.string.settings_reseal_done, result.converted)
                }
            } catch (e: Exception) {
                _message.value = e.message ?: e.javaClass.simpleName
            } finally {
                _busy.value = false
            }
        }
    }
}
