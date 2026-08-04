package com.bam.sshfs.backup

import com.bam.sshfs.crypto.SecretStore
import com.bam.sshfs.data.db.HostDao
import com.bam.sshfs.data.db.IdentityDao
import com.bam.sshfs.data.db.KeyDao
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.SshKey

/** How much a restore put back. */
data class RestoreResult(val keys: Int, val identities: Int, val hosts: Int)

/**
 * Writes a [BackupDocument] back into the database, re-sealing every secret under
 * *this* install's Keystore key.
 *
 * The restore **merges** rather than replaces: rows are inserted fresh and the
 * document's ids are remapped to the ids SQLite hands out, so a backup can be poured
 * into an install that already has keys and hosts without clobbering them. A name
 * that is already taken gets a suffix, because names are how the user tells rows
 * apart in the pickers.
 *
 * Insert order is keys → identities → hosts, matching the foreign keys; a reference
 * the document doesn't actually contain is dropped rather than failing the restore.
 */
class BackupRestorer(
    private val keys: KeyDao,
    private val identities: IdentityDao,
    private val hosts: HostDao,
    private val secrets: SecretStore,
) {

    suspend fun restore(document: BackupDocument): RestoreResult {
        val keyIds = restoreKeys(document)
        val identityIds = restoreIdentities(document, keyIds)
        val hostCount = restoreHosts(document, identityIds)
        return RestoreResult(keyIds.size, identityIds.size, hostCount)
    }

    /** @return old key id → newly inserted id. */
    private suspend fun restoreKeys(document: BackupDocument): Map<Long, Long> {
        val taken = keys.all().map { it.name }.toMutableSet()
        return document.keys.associate { key ->
            val id = keys.insert(
                SshKey(
                    name = uniqueName(key.name, taken),
                    type = key.type,
                    privateKeyCiphertext = secrets.encrypt(key.privateKey),
                    publicKey = key.publicKey,
                    hasPassphrase = key.hasPassphrase,
                    passphraseCiphertext = key.passphrase?.let(secrets::encrypt),
                    origin = key.origin,
                    createdAt = key.createdAt,
                ),
            )
            key.id to id
        }
    }

    /** @return old identity id → newly inserted id. */
    private suspend fun restoreIdentities(
        document: BackupDocument,
        keyIds: Map<Long, Long>,
    ): Map<Long, Long> {
        val taken = identities.all().map { it.name }.toMutableSet()
        return document.identities.associate { identity ->
            val id = identities.insert(
                Identity(
                    name = uniqueName(identity.name, taken),
                    username = identity.username,
                    passwordCiphertext = identity.password?.let(secrets::encrypt),
                    keyId = identity.keyId?.let(keyIds::get),
                    createdAt = identity.createdAt,
                ),
            )
            identity.id to id
        }
    }

    private suspend fun restoreHosts(
        document: BackupDocument,
        identityIds: Map<Long, Long>,
    ): Int {
        val taken = hosts.all().map { it.name }.toMutableSet()
        document.hosts.forEach { host ->
            hosts.insert(
                Host(
                    name = uniqueName(host.name, taken),
                    address = host.address,
                    port = host.port,
                    defaultIdentityId = host.defaultIdentityId?.let(identityIds::get),
                    remoteRoot = host.remoteRoot,
                    extraArgs = host.extraArgs,
                    createdAt = host.createdAt,
                ),
            )
        }
        return document.hosts.size
    }

    /** `name`, `name (2)`, `name (3)`… — the first spelling not already in [taken]. */
    private fun uniqueName(name: String, taken: MutableSet<String>): String {
        var candidate = name
        var suffix = 2
        while (!taken.add(candidate)) {
            candidate = "$name ($suffix)"
            suffix++
        }
        return candidate
    }
}
