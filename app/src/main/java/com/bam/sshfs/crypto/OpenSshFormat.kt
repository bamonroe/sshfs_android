package com.bam.sshfs.crypto

import com.bam.sshfs.data.model.KeyType
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.StringWriter
import java.security.MessageDigest
import java.util.Base64

/**
 * Reading and writing the on-the-wire OpenSSH text forms: the one-line public key,
 * its fingerprint, and PEM blocks for private keys.
 */
object OpenSshFormat {

    /** Serialise a public key as the `ssh-ed25519 AAAA… comment` line. */
    fun publicKeyLine(key: AsymmetricKeyParameter, comment: String): String {
        val blob = OpenSSHPublicKeyUtil.encodePublicKey(key)
        return publicKeyLine(blob, comment)
    }

    /** Same, from an already-encoded SSH wire blob. */
    fun publicKeyLine(blob: ByteArray, comment: String): String {
        val body = Base64.getEncoder().encodeToString(blob)
        val line = "${algorithmName(blob)} $body"
        return if (comment.isBlank()) line else "$line ${comment.trim()}"
    }

    /** Wrap DER bytes in a PEM block, e.g. `-----BEGIN RSA PRIVATE KEY-----`. */
    fun pem(label: String, der: ByteArray): String {
        val out = StringWriter()
        PemWriter(out).use { it.writeObject(PemObject(label, der)) }
        return out.toString()
    }

    /**
     * The `SHA256:…` fingerprint OpenSSH prints, computed over the wire blob of a
     * `ssh-… AAAA…` line. Returns null when the line doesn't parse.
     */
    fun fingerprint(publicKeyLine: String): String? {
        val blob = decodeBlob(publicKeyLine) ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    /** The key type a public-key line declares, or null when unrecognised. */
    fun typeOf(publicKeyLine: String): KeyType? =
        when (publicKeyLine.trim().substringBefore(' ')) {
            "ssh-ed25519" -> KeyType.ED25519
            "ssh-rsa", "rsa-sha2-256", "rsa-sha2-512" -> KeyType.RSA
            "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521" -> KeyType.ECDSA
            else -> null
        }

    private fun decodeBlob(publicKeyLine: String): ByteArray? {
        val body = publicKeyLine.trim().split(' ').getOrNull(1) ?: return null
        return runCatching { Base64.getDecoder().decode(body) }.getOrNull()
    }

    /** The algorithm name is the first length-prefixed string inside the blob. */
    private fun algorithmName(blob: ByteArray): String {
        require(blob.size >= 4) { "public key blob is truncated" }
        val len = ((blob[0].toInt() and 0xff) shl 24) or
            ((blob[1].toInt() and 0xff) shl 16) or
            ((blob[2].toInt() and 0xff) shl 8) or
            (blob[3].toInt() and 0xff)
        require(len in 1..(blob.size - 4)) { "public key blob is malformed" }
        return String(blob, 4, len, Charsets.US_ASCII)
    }
}
