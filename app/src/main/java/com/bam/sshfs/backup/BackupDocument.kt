package com.bam.sshfs.backup

import com.bam.sshfs.data.model.KeyOrigin
import com.bam.sshfs.data.model.KeyType

/**
 * The whole app configuration, in transit between the database and a backup file.
 *
 * Secrets appear here **in plaintext**: the database rows hold Keystore-sealed blobs
 * that only this install can open, so a backup that copied them verbatim would be
 * worthless on a new device. Unsealing here and re-sealing on restore is what makes
 * the file portable — and is exactly why the file itself must be encrypted under the
 * user's passphrase ([BackupCrypto]) before it ever reaches storage.
 *
 * Ids are the *export-local* ones; the restore side re-inserts and remaps them, so a
 * backup can be merged into an install that already has rows.
 */
data class BackupDocument(
    val version: Int = FORMAT_VERSION,
    val createdAt: Long,
    val keys: List<BackupKey> = emptyList(),
    val identities: List<BackupIdentity> = emptyList(),
    val hosts: List<BackupHost> = emptyList(),
) {
    companion object {
        /** Bumped when the on-disk shape changes; the reader refuses anything newer. */
        const val FORMAT_VERSION = 1
    }
}

/** A key pair with its private half in the clear. */
data class BackupKey(
    val id: Long,
    val name: String,
    val type: KeyType,
    val privateKey: String,
    val publicKey: String,
    val hasPassphrase: Boolean,
    val passphrase: String?,
    val origin: KeyOrigin,
    val createdAt: Long,
)

/** An identity with its password in the clear, referencing a [BackupKey.id]. */
data class BackupIdentity(
    val id: Long,
    val name: String,
    val username: String,
    val password: String?,
    val keyId: Long?,
    val createdAt: Long,
)

/** A host, referencing a [BackupIdentity.id]. Nothing here is secret. */
data class BackupHost(
    val id: Long,
    val name: String,
    val address: String,
    val port: Int,
    val defaultIdentityId: Long?,
    val remoteRoot: String,
    val extraArgs: String,
    val createdAt: Long,
)

/** The backup file was not readable: wrong shape, wrong version, or truncated. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
