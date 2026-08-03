package com.bam.sshfs.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bam.sshfs.data.model.Identity
import kotlinx.coroutines.flow.Flow

/** Reads and writes [Identity] rows. */
@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Identity>>

    @Query("SELECT * FROM identities WHERE id = :id")
    suspend fun byId(id: Long): Identity?

    /** How many hosts default to this identity — non-zero means deletion is blocked. */
    @Query("SELECT COUNT(*) FROM hosts WHERE defaultIdentityId = :id")
    suspend fun referenceCount(id: Long): Int

    /** Explicitly unlink a key before deleting it. */
    @Query("UPDATE identities SET keyId = NULL WHERE keyId = :keyId")
    suspend fun clearKeyLinks(keyId: Long)

    @Insert suspend fun insert(identity: Identity): Long
    @Update suspend fun update(identity: Identity)
    @Delete suspend fun delete(identity: Identity)
}
