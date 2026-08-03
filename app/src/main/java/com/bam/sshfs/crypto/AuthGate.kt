package com.bam.sshfs.crypto

/**
 * Asks the user to prove they're present — a fingerprint, a face, or the device PIN.
 *
 * Kept as an interface so the crypto layer never depends on an Activity: the UI
 * registers a [BiometricAuthGate] with [SecretAuthGate] while it is in the
 * foreground, and everything that needs an unlock goes through the holder.
 */
interface AuthGate {
    /** @return true when the user authenticated, false when they cancelled or failed. */
    suspend fun authenticate(title: String, subtitle: String): Boolean
}

/**
 * Raised when a secret is sealed under the authentication-gated key and the
 * unlock window has expired.
 *
 * Distinct from a plain [SecretStoreException] because it is *recoverable*: prompt
 * the user with an [AuthGate] and try the same blob again.
 */
class AuthenticationRequiredException(
    message: String = "This secret needs a fingerprint or device PIN to open.",
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The process-wide handle on whatever can currently show an authentication prompt.
 *
 * Decryption happens well away from the UI — a provider binder thread, the
 * connection service, a re-dial inside `ReconnectingSession` — so the code that
 * hits a locked key can't reach an Activity itself. The foreground Activity
 * registers here in `onStart` and clears in `onStop`; when nothing is registered
 * [authenticate] reports failure rather than blocking on a prompt no one can see.
 */
object SecretAuthGate {

    @Volatile
    private var gate: AuthGate? = null

    /** True while a foreground Activity can show a prompt. */
    val available: Boolean get() = gate != null

    fun register(gate: AuthGate) { this.gate = gate }

    /** Drop [gate] — only if it is still the current one, so a handoff can't clear it. */
    fun unregister(gate: AuthGate) { if (this.gate === gate) this.gate = null }

    /** Prompt through the registered gate; false when there is none. */
    suspend fun authenticate(title: String, subtitle: String): Boolean =
        gate?.authenticate(title, subtitle) ?: false
}
