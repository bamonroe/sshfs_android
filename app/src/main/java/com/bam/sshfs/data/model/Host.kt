package com.bam.sshfs.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** The default SSH port, used when the user leaves the field blank. */
const val DEFAULT_SSH_PORT = 22

/**
 * A remote server: where to reach it, which [Identity] to use by default, and
 * any extra ssh options (proxy/jump hosts, ciphers, keepalives) as freeform
 * `key=value` text passed through to the transport.
 *
 * Deleting an identity a host points at is **blocked** ([ForeignKey.RESTRICT]).
 */
@Entity(
    tableName = "hosts",
    foreignKeys = [
        ForeignKey(
            entity = Identity::class,
            parentColumns = ["id"],
            childColumns = ["defaultIdentityId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("defaultIdentityId")],
)
data class Host(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** User-facing label; also the SAF root title for this server. */
    val name: String,
    /** Hostname or IP. */
    val address: String,
    val port: Int = DEFAULT_SSH_PORT,
    val defaultIdentityId: Long? = null,
    /** Directory to open the SAF root at; defaults to the login directory. */
    val remoteRoot: String = ".",
    /** Freeform extra connect arguments, one `Option value` per line. */
    val extraArgs: String = "",
    val createdAt: Long,
)
