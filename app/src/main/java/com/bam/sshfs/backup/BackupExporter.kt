package com.bam.sshfs.backup

import com.bam.sshfs.crypto.SecretStore
import com.bam.sshfs.data.db.HostDao
import com.bam.sshfs.data.db.IdentityDao
import com.bam.sshfs.data.db.KeyDao

/**
 * Reads the whole configuration out of the database and unseals it into a
 * [BackupDocument].
 *
 * Everything this produces is plaintext secret material, so the caller must hand it
 * straight to [BackupCrypto.seal] and never let it touch disk unsealed.
 */
class BackupExporter(
    private val keys: KeyDao,
    private val identities: IdentityDao,
    private val hosts: HostDao,
    private val secrets: SecretStore,
) {

    /**
     * Every sealed blob the export will have to open.
     *
     * The caller passes these to `Secrets.unlockForRead` so the authentication prompt
     * happens once, up front, instead of once per row half-way through the pass.
     */
    suspend fun sealedSecrets(): List<String?> =
        keys.all().flatMap { listOf(it.privateKeyCiphertext, it.passphraseCiphertext) } +
            identities.all().map { it.passwordCiphertext }

    /** Unseal the whole configuration. [now] stamps the document. */
    suspend fun collect(now: Long): BackupDocument = BackupDocument(
        createdAt = now,
        keys = keys.all().map { key ->
            BackupKey(
                id = key.id,
                name = key.name,
                type = key.type,
                privateKey = secrets.decrypt(key.privateKeyCiphertext),
                publicKey = key.publicKey,
                hasPassphrase = key.hasPassphrase,
                passphrase = key.passphraseCiphertext?.let(secrets::decrypt),
                origin = key.origin,
                createdAt = key.createdAt,
            )
        },
        identities = identities.all().map { identity ->
            BackupIdentity(
                id = identity.id,
                name = identity.name,
                username = identity.username,
                password = identity.passwordCiphertext?.let(secrets::decrypt),
                keyId = identity.keyId,
                createdAt = identity.createdAt,
            )
        },
        hosts = hosts.all().map { host ->
            BackupHost(
                id = host.id,
                name = host.name,
                address = host.address,
                port = host.port,
                defaultIdentityId = host.defaultIdentityId,
                remoteRoot = host.remoteRoot,
                extraArgs = host.extraArgs,
                createdAt = host.createdAt,
            )
        },
    )
}
