package com.bam.sshfs.crypto

import com.bam.sshfs.data.db.IdentityDao
import com.bam.sshfs.data.db.KeyDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Re-seals every stored secret under the scheme the user just chose.
 *
 * Turning the authentication gate on or off only changes what *new* writes use, so
 * without this pass the old rows would keep their old prefix — a password sealed
 * before the switch would stay readable without a fingerprint, which is not what
 * "require authentication" means. Run it right after flipping the setting, while the
 * user is still authenticated: reading a `v2:` blob to convert it back needs the
 * unlock window to be open.
 *
 * Each row is read and written independently, so a failure part-way leaves a mix of
 * schemes rather than a broken store — both prefixes stay readable, and re-running
 * finishes the job.
 */
class SecretResealer(
    private val keys: KeyDao,
    private val identities: IdentityDao,
) {

    /** How many secrets were converted, and how many refused to convert. */
    data class Result(val converted: Int, val failed: Int)

    /**
     * Convert every blob to [gated]'s scheme.
     *
     * @throws AuthenticationRequiredException if the unlock window closed before the
     *   first gated blob could be read — nothing is written in that case.
     */
    suspend fun reseal(gated: Boolean): Result = withContext(Dispatchers.IO) {
        val writer = KeystoreSecretStore(gated = { gated })
        var converted = 0
        var failed = 0

        keys.all().forEach { key ->
            // A placeholder key has no private blob to convert; "" stays "" rather than
            // failing the row and stalling the whole pass.
            val private = if (key.hasPrivateHalf) convert(key.privateKeyCiphertext, writer) else ""
            val passphrase = key.passphraseCiphertext?.let { convert(it, writer) }
            if (private == null || (key.passphraseCiphertext != null && passphrase == null)) {
                failed++
            } else {
                keys.update(
                    key.copy(privateKeyCiphertext = private, passphraseCiphertext = passphrase),
                )
                converted++
            }
        }

        identities.all().forEach { identity ->
            val password = identity.passwordCiphertext ?: return@forEach
            val next = convert(password, writer)
            if (next == null) {
                failed++
            } else {
                identities.update(identity.copy(passwordCiphertext = next))
                converted++
            }
        }

        Result(converted, failed)
    }

    /**
     * Read [blob] with whichever scheme wrote it and hand it back sealed by [writer].
     *
     * A blob that won't open at all (the Keystore key was wiped by a credential
     * reset) is left alone and counted as a failure — overwriting it would destroy
     * the only copy, and the user may still want to see which entry to re-enter.
     */
    private fun convert(blob: String, writer: SecretStore): String? = try {
        writer.encrypt(reader.decrypt(blob))
    } catch (e: SecretStoreException) {
        null
    }

    /** Reads either scheme; its `gated` flag only affects writes, which it never does. */
    private val reader = KeystoreSecretStore()
}
