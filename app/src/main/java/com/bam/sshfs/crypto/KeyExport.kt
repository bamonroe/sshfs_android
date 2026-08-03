package com.bam.sshfs.crypto

import com.bam.sshfs.data.model.KeyType
import com.bam.sshfs.data.model.SshKey

/**
 * The naming and framing rules for handing a private key back to the user.
 *
 * Export gives back the *stored* text verbatim — the same PEM/OpenSSH block that
 * was generated or imported — so a key that carries a passphrase stays protected
 * in the exported file, and an unprotected one lands as plaintext key material.
 * Android-free like the rest of `crypto/`; the actual write lives in the caller.
 */
object KeyExport {

    /**
     * The name to offer in the save dialog: `ssh-keygen`'s convention for the
     * algorithm, disambiguated by the key's own name.
     */
    fun suggestedFileName(key: SshKey): String {
        val stem = when (key.type) {
            KeyType.ED25519 -> "id_ed25519"
            KeyType.ECDSA -> "id_ecdsa"
            KeyType.RSA -> "id_rsa"
        }
        val slug = key.name.trim().lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
        return if (slug.isEmpty()) stem else "${stem}_$slug"
    }

    /** The file's contents: the key as stored, newline-terminated as a key file must be. */
    fun fileContents(privateKey: String): String = privateKey.trimEnd() + "\n"
}
