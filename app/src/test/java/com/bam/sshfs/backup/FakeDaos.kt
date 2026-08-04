package com.bam.sshfs.backup

import com.bam.sshfs.data.db.HostDao
import com.bam.sshfs.data.db.IdentityDao
import com.bam.sshfs.data.db.KeyDao
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.SshKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory stand-ins for the three DAOs, so the backup round trip can be tested on
 * the desktop JVM. They implement only the queries the backup path uses; everything
 * else is enough to satisfy the interface.
 */
class FakeKeyDao(private val rows: MutableList<SshKey> = mutableListOf()) : KeyDao {
    private var nextId = 1L
    override fun observeAll(): Flow<List<SshKey>> = flowOf(rows)
    override suspend fun byId(id: Long) = rows.firstOrNull { it.id == id }
    override suspend fun all(): List<SshKey> = rows.toList()
    override suspend fun referenceCount(id: Long) = 0
    override suspend fun insert(key: SshKey): Long =
        nextId++.also { rows += key.copy(id = it) }
    override suspend fun update(key: SshKey) {
        rows.replaceAll { if (it.id == key.id) key else it }
    }
    override suspend fun delete(key: SshKey) { rows.removeIf { it.id == key.id } }
}

class FakeIdentityDao(private val rows: MutableList<Identity> = mutableListOf()) : IdentityDao {
    private var nextId = 1L
    override fun observeAll(): Flow<List<Identity>> = flowOf(rows)
    override suspend fun byId(id: Long) = rows.firstOrNull { it.id == id }
    override suspend fun all(): List<Identity> = rows.toList()
    override suspend fun referenceCount(id: Long) = 0
    override suspend fun clearKeyLinks(keyId: Long) {
        rows.replaceAll { if (it.keyId == keyId) it.copy(keyId = null) else it }
    }
    override suspend fun insert(identity: Identity): Long =
        nextId++.also { rows += identity.copy(id = it) }
    override suspend fun update(identity: Identity) {
        rows.replaceAll { if (it.id == identity.id) identity else it }
    }
    override suspend fun delete(identity: Identity) { rows.removeIf { it.id == identity.id } }
}

class FakeHostDao(private val rows: MutableList<Host> = mutableListOf()) : HostDao {
    private var nextId = 1L
    override fun observeAll(): Flow<List<Host>> = flowOf(rows)
    override suspend fun byId(id: Long) = rows.firstOrNull { it.id == id }
    override suspend fun all(): List<Host> = rows.toList()
    override suspend fun clearIdentityLinks(identityId: Long) {
        rows.replaceAll {
            if (it.defaultIdentityId == identityId) it.copy(defaultIdentityId = null) else it
        }
    }
    override suspend fun insert(host: Host): Long =
        nextId++.also { rows += host.copy(id = it) }
    override suspend fun update(host: Host) {
        rows.replaceAll { if (it.id == host.id) host else it }
    }
    override suspend fun delete(host: Host) { rows.removeIf { it.id == host.id } }
}
