package com.bam.sshfs.crypto

import java.util.Base64

/**
 * Turns a plaintext secret into the ciphertext blob a database row holds, and back.
 *
 * [KeystoreSecretStore] is the real implementation and the one the app wires up;
 * [PassthroughSecretStore] remains for unit tests, which run on a desktop JVM with
 * no `AndroidKeyStore` provider behind them.
 */
interface SecretStore {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

/** A secret could not be sealed or opened — a missing key, or a tampered blob. */
class SecretStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Base64 only — **not** encryption. Used by unit tests, and by [KeystoreSecretStore]
 * as the reader for rows written before encryption was wired in.
 */
class PassthroughSecretStore : SecretStore {
    override fun encrypt(plaintext: String): String =
        Base64.getEncoder().encodeToString(plaintext.toByteArray(Charsets.UTF_8))

    override fun decrypt(ciphertext: String): String =
        String(Base64.getDecoder().decode(ciphertext), Charsets.UTF_8)
}
