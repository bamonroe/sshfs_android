package com.bam.sshfs.backup

import com.bam.sshfs.crypto.SecretStore
import com.bam.sshfs.data.db.HostDao
import com.bam.sshfs.data.db.IdentityDao
import com.bam.sshfs.data.db.KeyDao
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.SshKey

/**
 * How much a restore put back.
 *
 * [incompleteKeys] names the keys that came out of a config-only file with no private
 * half — restored as placeholders, and unusable until the user supplies the material.
 * They are named rather than counted because the user has to go find each one.
 */
data class RestoreResult(
    val keys: Int,
    val identities: Int,
    val hosts: Int,
    val incompleteKeys: List<String> = emptyList(),
)

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
        val restoredKeys = restoreKeys(document)
        val identityIds = restoreIdentities(document, restoredKeys.ids)
        val hostCount = restoreHosts(document, identityIds)
        return RestoreResult(
            keys = restoredKeys.ids.size,
            identities = identityIds.size,
            hosts = hostCount,
            incompleteKeys = restoredKeys.incomplete,
        )
    }

    /** The id remapping for the restored keys, plus the names of the placeholder ones. */
    private data class RestoredKeys(val ids: Map<Long, Long>, val incomplete: List<String>)

    private suspend fun restoreKeys(document: BackupDocument): RestoredKeys {
        val taken = keys.all().map { it.name }.toMutableSet()
        val ids = mutableMapOf<Long, Long>()
        val incomplete = mutableListOf<String>()
        document.keys.forEach { key ->
            val name = uniqueName(key.name, taken)
            // A placeholder's empty private half is stored as-is: sealing "" would give
            // a blob that decrypts to nothing, which reads as a usable key everywhere.
            val placeholder = ConfigOnly.isPlaceholder(key)
            if (placeholder) incomplete += name
            ids[key.id] = keys.insert(
                SshKey(
                    name = name,
                    type = key.type,
                    privateKeyCiphertext = if (placeholder) "" else secrets.encrypt(key.privateKey),
                    publicKey = key.publicKey,
                    hasPassphrase = key.hasPassphrase,
                    passphraseCiphertext = key.passphrase?.let(secrets::encrypt),
                    origin = key.origin,
                    createdAt = key.createdAt,
                ),
            )
        }
        return RestoredKeys(ids, incomplete)
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
