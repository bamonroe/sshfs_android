package com.bam.sshfs.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's handful of user preferences, on top of plain `SharedPreferences`.
 *
 * Nothing secret lives here — only switches — so this is deliberately *not* the
 * encrypted store; the secrets themselves are sealed by
 * [com.bam.sshfs.crypto.KeystoreSecretStore] and live in the database.
 *
 * Reads are synchronous because the secret store has to consult
 * [requireAuthentication] from arbitrary threads on every seal; the backing map is
 * already in memory after the first load, and the observable [StateFlow] is what the
 * UI collects.
 */
class AppSettings private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _requireAuthentication =
        MutableStateFlow(prefs.getBoolean(KEY_REQUIRE_AUTH, false))

    /**
     * Whether new secrets are sealed under the authentication-gated Keystore key.
     *
     * Off by default: turning it on is a decision with a cost (a prompt before every
     * connect, and secrets that a credential reset destroys), so it isn't imposed.
     */
    val requireAuthentication: StateFlow<Boolean> = _requireAuthentication.asStateFlow()

    /** The current value, for callers that can't collect a flow. */
    fun requireAuthenticationNow(): Boolean = _requireAuthentication.value

    fun setRequireAuthentication(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_AUTH, enabled).apply()
        _requireAuthentication.value = enabled
    }

    companion object {
        private const val FILE = "sshfs_settings"
        private const val KEY_REQUIRE_AUTH = "require_authentication"

        @Volatile
        private var instance: AppSettings? = null

        /** Process-wide singleton — the provider, the service and the UI share one. */
        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}
