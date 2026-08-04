package com.bam.sshfs.net.ssh

import com.bam.sshfs.crypto.SecretStore
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.SshKey

/**
 * The plaintext credentials one connection attempt needs, decrypted from an
 * [Identity] and its optional [SshKey] just before use.
 *
 * Deliberately a short-lived value: nothing holds one across connections, so
 * decrypted secrets don't sit in memory longer than the handshake needs them.
 */
data class SshCredentials(
    val username: String,
    /** Plaintext password, or null when this identity authenticates by key only. */
    val password: String? = null,
    /** The private key in its OpenSSH/PEM text form, or null for password-only. */
    val privateKey: String? = null,
    /** Passphrase protecting [privateKey], or null when the key is unencrypted. */
    val passphrase: String? = null,
) {
    /** True when there is at least one credential to offer the server. */
    val hasCredential: Boolean get() = !password.isNullOrEmpty() || !privateKey.isNullOrBlank()
}

/**
 * Turns the ciphertext columns of an identity (and the key it links to) into
 * usable [SshCredentials].
 *
 * Lives in the transport layer rather than the UI because decryption is the
 * connection manager's job — the editor screens never read a secret back.
 */
class CredentialResolver(private val secrets: SecretStore) {

    /** @throws com.bam.sshfs.crypto.SecretStoreException if a stored blob won't open. */
    fun resolve(identity: Identity, key: SshKey?): SshCredentials =
        SshCredentials(
            username = identity.username.trim(),
            password = identity.passwordCiphertext?.let(secrets::decrypt),
            // A placeholder key has no private half to decrypt; the connection falls
            // back to whatever else the identity offers rather than failing here.
            privateKey = key?.takeIf { it.hasPrivateHalf }?.let { secrets.decrypt(it.privateKeyCiphertext) },
            passphrase = key?.passphraseCiphertext?.let(secrets::decrypt),
        )
}
