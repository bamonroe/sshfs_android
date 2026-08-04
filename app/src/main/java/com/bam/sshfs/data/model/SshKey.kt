package com.bam.sshfs.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** How a key pair came to live in the app. */
enum class KeyOrigin { GENERATED, IMPORTED }

/** The key pair's algorithm. */
enum class KeyType { ED25519, RSA, ECDSA }

/**
 * An SSH key pair, either generated on-device or imported by the user.
 *
 * The private material is stored already encrypted by the Android Keystore
 * (see the credential-storage task); this row never holds plaintext.
 */
@Entity(tableName = "ssh_keys")
data class SshKey(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** User-facing label, unique enough to pick from a list. */
    val name: String,
    val type: KeyType,
    /**
     * Keystore-encrypted private key blob, base64.
     *
     * Empty for a **placeholder** key — one restored from a config-only export, which
     * carries the public half and every link to the key but no private material. Ask
     * [hasPrivateHalf] rather than testing this column by hand.
     */
    val privateKeyCiphertext: String,
    /** OpenSSH-format public key, safe to store in the clear. */
    val publicKey: String,
    /** True when the private key itself is passphrase-protected. */
    val hasPassphrase: Boolean = false,
    /** Keystore-encrypted passphrase, or null when [hasPassphrase] is false. */
    val passphraseCiphertext: String? = null,
    val origin: KeyOrigin,
    val createdAt: Long,
) {
    /** False for a placeholder: the key can't authenticate or be exported until filled in. */
    val hasPrivateHalf: Boolean get() = privateKeyCiphertext.isNotEmpty()
}
