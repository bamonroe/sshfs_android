package com.bam.sshfs.crypto

import com.bam.sshfs.data.model.KeyType

/**
 * A key pair as text, the way OpenSSH would write it to disk.
 *
 * [privateKey] is whatever PEM/OpenSSH block the pair came as — generated here or
 * pasted in by the user — and is the exact string that gets encrypted into
 * `SshKey.privateKeyCiphertext`. [publicKey] is the single-line
 * `ssh-ed25519 AAAA… comment` form that goes into a server's `authorized_keys`.
 */
data class KeyMaterial(
    val type: KeyType,
    val privateKey: String,
    val publicKey: String,
    /** True when [privateKey] is itself passphrase-protected. */
    val encrypted: Boolean = false,
)

/** The private key text was malformed, unsupported, or the passphrase was wrong. */
class KeyMaterialException(message: String, cause: Throwable? = null) : Exception(message, cause)
