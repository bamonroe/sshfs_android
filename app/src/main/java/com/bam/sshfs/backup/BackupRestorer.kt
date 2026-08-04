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
    val skipped: Int = 0,
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
 * An element whose [BackupHashes] content hash is already present locally is **skipped**
 * — not inserted under a suffixed name — and everything pointing at it is remapped to
 * the row that is already there. That is what makes restoring the same backup twice, or
 * merging two backups taken from the same install, idempotent instead of duplicating.
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
        val hashes = BackupHashes.of(document)
        val restoredKeys = restoreKeys(document, hashes)
        val restoredIdentities = restoreIdentities(document, hashes, restoredKeys.ids)
        val restoredHosts = restoreHosts(document, hashes, restoredIdentities.ids)
        return RestoreResult(
            keys = restoredKeys.inserted,
            identities = restoredIdentities.inserted,
            hosts = restoredHosts.inserted,
            incompleteKeys = restoredKeys.incomplete,
            skipped = restoredKeys.skipped + restoredIdentities.skipped + restoredHosts.skipped,
        )
    }

    /**
     * What one element type's pass did.
     *
     * [ids] maps every document id to a local row — the one just inserted, or the
     * already-present row a matching hash pointed at — so the elements that reference
     * it land on the right target either way.
     */
    private data class Restored(
        val ids: Map<Long, Long>,
        val inserted: Int,
        val skipped: Int,
        val incomplete: List<String> = emptyList(),
    )

    private suspend fun restoreKeys(
        document: BackupDocument,
        hashes: DocumentHashes,
    ): Restored {
        val local = keys.all()
        val existing = local.associateBy({ BackupHashes.key(it) }, { it.id })
        val taken = local.map { it.name }.toMutableSet()
        val ids = mutableMapOf<Long, Long>()
        val incomplete = mutableListOf<String>()
        var inserted = 0
        var skipped = 0
        document.keys.forEach { key ->
            val match = existing[hashes.keys[key.id]]
            if (match != null) {
                ids[key.id] = match
                skipped++
                return@forEach
            }
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
            inserted++
        }
        return Restored(ids, inserted, skipped, incomplete)
    }

    private suspend fun restoreIdentities(
        document: BackupDocument,
        hashes: DocumentHashes,
        keyIds: Map<Long, Long>,
    ): Restored {
        val local = identities.all()
        // Local hashes have to resolve their key reference the same way the document's
        // do, so a match means "same identity pointing at the same key".
        val localKeyHashes = keys.all().associate { it.id to BackupHashes.key(it) }
        val existing = local.associateBy(
            { BackupHashes.identity(it, it.keyId?.let(localKeyHashes::get)) },
            { it.id },
        )
        val taken = local.map { it.name }.toMutableSet()
        val ids = mutableMapOf<Long, Long>()
        var inserted = 0
        var skipped = 0
        document.identities.forEach { identity ->
            val match = existing[hashes.identities[identity.id]]
            if (match != null) {
                ids[identity.id] = match
                skipped++
                return@forEach
            }
            ids[identity.id] = identities.insert(
                Identity(
                    name = uniqueName(identity.name, taken),
                    username = identity.username,
                    passwordCiphertext = identity.password?.let(secrets::encrypt),
                    keyId = identity.keyId?.let(keyIds::get),
                    createdAt = identity.createdAt,
                ),
            )
            inserted++
        }
        return Restored(ids, inserted, skipped)
    }

    private suspend fun restoreHosts(
        document: BackupDocument,
        hashes: DocumentHashes,
        identityIds: Map<Long, Long>,
    ): Restored {
        val local = hosts.all()
        val localKeyHashes = keys.all().associate { it.id to BackupHashes.key(it) }
        val localIdentityHashes = identities.all().associate {
            it.id to BackupHashes.identity(it, it.keyId?.let(localKeyHashes::get))
        }
        val existing = local.associateBy(
            { BackupHashes.host(it, it.defaultIdentityId?.let(localIdentityHashes::get)) },
            { it.id },
        )
        val taken = local.map { it.name }.toMutableSet()
        val ids = mutableMapOf<Long, Long>()
        var inserted = 0
        var skipped = 0
        document.hosts.forEach { host ->
            val match = existing[hashes.hosts[host.id]]
            if (match != null) {
                ids[host.id] = match
                skipped++
                return@forEach
            }
            ids[host.id] = hosts.insert(
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
            inserted++
        }
        return Restored(ids, inserted, skipped)
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
