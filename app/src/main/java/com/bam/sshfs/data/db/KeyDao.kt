package com.bam.sshfs.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bam.sshfs.data.model.SshKey
import kotlinx.coroutines.flow.Flow

/** Reads and writes [SshKey] rows. */
@Dao
interface KeyDao {
    @Query("SELECT * FROM ssh_keys ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SshKey>>

    @Query("SELECT * FROM ssh_keys WHERE id = :id")
    suspend fun byId(id: Long): SshKey?

    /** How many identities point at this key — non-zero means deletion is blocked. */
    @Query("SELECT COUNT(*) FROM identities WHERE keyId = :id")
    suspend fun referenceCount(id: Long): Int

    @Insert suspend fun insert(key: SshKey): Long
    @Update suspend fun update(key: SshKey)
    @Delete suspend fun delete(key: SshKey)
}
