package com.bam.sshfs.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bam.sshfs.data.model.Host
import kotlinx.coroutines.flow.Flow

/** Reads and writes [Host] rows. */
@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Host>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun byId(id: Long): Host?

    /** Every row, once — the backup pass walks the whole table. */
    @Query("SELECT * FROM hosts ORDER BY id")
    suspend fun all(): List<Host>

    /** Explicitly clear a default identity before deleting it. */
    @Query("UPDATE hosts SET defaultIdentityId = NULL WHERE defaultIdentityId = :identityId")
    suspend fun clearIdentityLinks(identityId: Long)

    @Insert suspend fun insert(host: Host): Long
    @Update suspend fun update(host: Host)
    @Delete suspend fun delete(host: Host)
}
