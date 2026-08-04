package com.bam.sshfs.backup

import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seals a backup under a user-supplied passphrase.
 *
 * The Keystore key that protects secrets on the device deliberately cannot leave it,
 * so a portable backup needs its own key — derived from the passphrase with PBKDF2
 * and used for one AES-256-GCM pass over the whole document. GCM's tag is what makes
 * a wrong passphrase report itself as "wrong passphrase" rather than as garbage.
 *
 * The file is a small header line followed by base64 fields, so the format is
 * self-describing and a future iteration count or cipher can be told apart from this
 * one. Deliberately Android-free — `javax.crypto` alone — so it is unit-testable.
 */
object BackupCrypto {

    /** Marks the file and pins the envelope shape. */
    const val MAGIC = "sshfs-backup-v1"

    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * Encrypt [plaintext] under [passphrase], returning the file's full text.
     *
     * [random] is injectable so tests can pin the salt and IV; production takes the
     * default `SecureRandom`.
     */
    fun seal(plaintext: String, passphrase: String, random: SecureRandom = SecureRandom()): String {
        require(passphrase.isNotEmpty()) { "A backup passphrase cannot be empty." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        val sealed = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return buildString {
            appendLine(MAGIC)
            appendLine("kdf: $KDF")
            appendLine("iterations: $ITERATIONS")
            appendLine("salt: ${salt.b64()}")
            appendLine("iv: ${iv.b64()}")
            appendLine("data: ${sealed.b64()}")
        }
    }

    /**
     * Decrypt a file produced by [seal].
     *
     * @throws BackupFormatException if the text isn't a backup file at all.
     * @throws WrongPassphraseException if it is, but the passphrase doesn't open it.
     */
    fun open(fileText: String, passphrase: String): String {
        val fields = parse(fileText)
        val iterations = fields["iterations"]?.toIntOrNull()
            ?: throw BackupFormatException("The backup file has no iteration count.")
        val salt = fields.bytes("salt")
        val iv = fields.bytes("iv")
        val data = fields.bytes("data")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                deriveKey(passphrase, salt, iterations),
                GCMParameterSpec(TAG_BITS, iv),
            )
        }
        val opened = try {
            cipher.doFinal(data)
        } catch (e: AEADBadTagException) {
            throw WrongPassphraseException(cause = e)
        } catch (e: GeneralSecurityException) {
            throw WrongPassphraseException(cause = e)
        }
        return String(opened, StandardCharsets.UTF_8)
    }

    /** True when [fileText] looks like one of our sealed backups. */
    fun isSealed(fileText: String): Boolean = fileText.trimStart().startsWith(MAGIC)

    private fun parse(fileText: String): Map<String, String> {
        val lines = fileText.trim().lines()
        if (lines.firstOrNull()?.trim() != MAGIC) {
            throw BackupFormatException("This file is not an encrypted sshfs backup.")
        }
        return lines.drop(1)
            .mapNotNull { line ->
                val at = line.indexOf(':')
                if (at <= 0) null else line.take(at).trim() to line.substring(at + 1).trim()
            }
            .toMap()
    }

    private fun Map<String, String>.bytes(name: String): ByteArray {
        val value = this[name] ?: throw BackupFormatException("The backup file has no '$name'.")
        return try {
            Base64.getDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("The backup file's '$name' field is corrupt.", e)
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int = ITERATIONS) =
        SecretKeyFactory.getInstance(KDF)
            .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS))
            .encoded
            .let { SecretKeySpec(it, "AES") }

    private fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)
}

/** The passphrase did not open the backup — wrong passphrase, or a tampered file. */
class WrongPassphraseException(
    message: String = "That passphrase did not open the backup file.",
    cause: Throwable? = null,
) : Exception(message, cause)
