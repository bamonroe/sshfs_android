package com.bam.sshfs.data.repo

import com.bam.sshfs.data.db.HostDao
import com.bam.sshfs.data.model.Host
import kotlinx.coroutines.flow.Flow

/** Host CRUD. Nothing references a host, so deletes are unconditional. */
class HostRepository(private val hosts: HostDao) {
    fun observeAll(): Flow<List<Host>> = hosts.observeAll()

    suspend fun byId(id: Long): Host? = hosts.byId(id)

    suspend fun save(host: Host): Long =
        if (host.id == 0L) hosts.insert(host) else host.id.also { hosts.update(host) }

    suspend fun delete(host: Host) = hosts.delete(host)
}
