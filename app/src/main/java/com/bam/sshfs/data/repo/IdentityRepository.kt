package com.bam.sshfs.data.repo

import com.bam.sshfs.data.db.HostDao
import com.bam.sshfs.data.db.IdentityDao
import com.bam.sshfs.data.model.Identity
import kotlinx.coroutines.flow.Flow

/** Identity CRUD plus the referential rules for deleting one. */
class IdentityRepository(
    private val identities: IdentityDao,
    private val hosts: HostDao,
) {
    fun observeAll(): Flow<List<Identity>> = identities.observeAll()

    suspend fun byId(id: Long): Identity? = identities.byId(id)

    suspend fun save(identity: Identity): Long =
        if (identity.id == 0L) identities.insert(identity) else identity.id.also { identities.update(identity) }

    /**
     * Delete an identity. Blocked while any host defaults to it unless [unlink]
     * is set, in which case those hosts lose their default identity first.
     */
    suspend fun delete(identity: Identity, unlink: Boolean = false) {
        val refs = identities.referenceCount(identity.id)
        if (refs > 0) {
            if (!unlink) {
                throw ReferencedException(refs, "Identity '${identity.name}' is used by $refs hosts")
            }
            hosts.clearIdentityLinks(identity.id)
        }
        identities.delete(identity)
    }
}
