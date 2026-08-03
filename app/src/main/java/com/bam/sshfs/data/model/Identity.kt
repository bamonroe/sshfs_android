package com.bam.sshfs.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A way to authenticate: a username plus an optional password and/or a link to
 * an [SshKey]. Either credential may be absent (agent-less keyboard-interactive
 * prompts are resolved at connect time), but an identity with neither cannot
 * connect.
 *
 * Deleting a key that an identity references is **blocked** ([ForeignKey.RESTRICT]);
 * the UI must clear the link first.
 */
@Entity(
    tableName = "identities",
    foreignKeys = [
        ForeignKey(
            entity = SshKey::class,
            parentColumns = ["id"],
            childColumns = ["keyId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("keyId")],
)
data class Identity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** User-facing label. */
    val name: String,
    val username: String,
    /** Keystore-encrypted password, or null when only a key is used. */
    val passwordCiphertext: String? = null,
    /** The key this identity authenticates with, or null for password-only. */
    val keyId: Long? = null,
    val createdAt: Long,
)
