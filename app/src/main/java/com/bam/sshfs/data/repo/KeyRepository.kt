package com.bam.sshfs.data.repo

import com.bam.sshfs.data.db.IdentityDao
import com.bam.sshfs.data.db.KeyDao
import com.bam.sshfs.data.model.SshKey
import kotlinx.coroutines.flow.Flow

/** Key CRUD plus the referential rules for deleting one. */
class KeyRepository(
    private val keys: KeyDao,
    private val identities: IdentityDao,
) {
    fun observeAll(): Flow<List<SshKey>> = keys.observeAll()

    suspend fun byId(id: Long): SshKey? = keys.byId(id)

    suspend fun save(key: SshKey): Long =
        if (key.id == 0L) keys.insert(key) else key.id.also { keys.update(key) }

    /**
     * Delete a key. Blocked while any identity references it unless [unlink] is
     * set, in which case those identities have their key link cleared first.
     */
    suspend fun delete(key: SshKey, unlink: Boolean = false) {
        val refs = keys.referenceCount(key.id)
        if (refs > 0) {
            if (!unlink) {
                throw ReferencedException(refs, "Key '${key.name}' is used by $refs identities")
            }
            identities.clearKeyLinks(key.id)
        }
        keys.delete(key)
    }
}
