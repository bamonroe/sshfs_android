package com.bam.sshfs.crypto

import java.util.Base64

/**
 * Turns a plaintext secret into the ciphertext blob a database row holds, and back.
 *
 * The Keystore-backed implementation lands with the credential-storage task; until
 * then [PassthroughSecretStore] keeps the call sites — and the column contract —
 * exactly as they will be once real encryption is wired in.
 */
interface SecretStore {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

/**
 * Base64 only — **not** encryption. A placeholder so the repository and UI already
 * speak in ciphertext blobs; swapping in the Keystore implementation is then a
 * one-line change with no schema or call-site churn.
 */
class PassthroughSecretStore : SecretStore {
    override fun encrypt(plaintext: String): String =
        Base64.getEncoder().encodeToString(plaintext.toByteArray(Charsets.UTF_8))

    override fun decrypt(ciphertext: String): String =
        String(Base64.getDecoder().decode(ciphertext), Charsets.UTF_8)
}
