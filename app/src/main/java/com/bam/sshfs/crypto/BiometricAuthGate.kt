package com.bam.sshfs.crypto

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * An [AuthGate] backed by `BiometricPrompt` on the hosting Activity.
 *
 * Accepts a strong biometric *or* the device credential, so a device with no
 * enrolled fingerprint still gates on the PIN/pattern/password. No `CryptoObject`
 * is passed: the gated Keystore key is time-bound (see [KeystoreSecretStore]), so a
 * successful prompt unlocks it for the whole window rather than for one cipher.
 */
class BiometricAuthGate(private val activity: FragmentActivity) : AuthGate {

    override suspend fun authenticate(title: String, subtitle: String): Boolean =
        // BiometricPrompt touches the fragment manager, so it must be shown on main.
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult,
                        ) {
                            if (continuation.isActive) continuation.resume(true)
                        }

                        // A hard error — cancelled, locked out, no hardware. A single
                        // *failed* attempt doesn't land here; the prompt retries itself.
                        override fun onAuthenticationError(code: Int, message: CharSequence) {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    },
                )
                continuation.invokeOnCancellation { prompt.cancelAuthentication() }
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(ALLOWED)
                        .build(),
                )
            }
        }

    companion object {
        private const val ALLOWED = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        /**
         * True when this device can gate secrets at all.
         *
         * False on a device with no enrolled biometric *and* no screen lock — there
         * the gate would have nothing to ask for, and a gated Keystore key can't
         * even be generated, so the setting must stay off.
         */
        fun canAuthenticate(context: Context): Boolean =
            BiometricManager.from(context).canAuthenticate(ALLOWED) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }
}
