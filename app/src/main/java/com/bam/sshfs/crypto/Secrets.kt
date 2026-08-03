package com.bam.sshfs.crypto

import android.content.Context
import com.bam.sshfs.data.settings.AppSettings

/**
 * The one place that builds the app's [SecretStore].
 *
 * Every seal has to know whether the user asked for the authentication gate, and
 * that answer lives in [AppSettings] — wiring it here keeps the call sites (two
 * ViewModels and the connection manager) from each having to remember to pass it,
 * and keeps `KeystoreSecretStore` itself free of a `Context`.
 */
object Secrets {

    /** A store whose writes follow the current "require authentication" setting. */
    fun store(context: Context): SecretStore {
        val settings = AppSettings.get(context)
        return KeystoreSecretStore(gated = settings::requireAuthenticationNow)
    }

    /**
     * Prompt for authentication when the gate is on, so a following seal succeeds.
     *
     * The gated Keystore key requires the user to be authenticated to *encrypt* as
     * well as to decrypt, so saving a key or a password has to open the window too.
     * A no-op when the setting is off.
     *
     * @throws AuthenticationRequiredException if the user declines or no prompt can
     *   be shown, leaving the caller's write undone rather than half-done.
     */
    suspend fun unlockForWrite(context: Context, title: String, subtitle: String) {
        if (!AppSettings.get(context).requireAuthenticationNow()) return
        if (!SecretAuthGate.authenticate(title, subtitle)) {
            throw AuthenticationRequiredException()
        }
    }
}
