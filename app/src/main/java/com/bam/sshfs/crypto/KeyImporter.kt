package com.bam.sshfs.crypto

import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.userauth.keyprovider.FileKeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.StringReader
import java.util.Base64

/**
 * Parses a private key the user pasted or picked from the file picker.
 *
 * Parsing goes through SSHJ — the same library the transport will authenticate
 * with — so an import that succeeds here is one that will actually connect, and
 * the public key we show is derived from the private material rather than trusted
 * from a separate `.pub` file.
 */
object KeyImporter {

    /** Whether [text] is passphrase-protected, i.e. importing it needs a prompt. */
    fun isEncrypted(text: String): Boolean {
        val trimmed = text.trim()
        return when {
            trimmed.contains("Proc-Type: 4,ENCRYPTED") -> true
            trimmed.contains("BEGIN ENCRYPTED PRIVATE KEY") -> true
            trimmed.contains("BEGIN OPENSSH PRIVATE KEY") -> openSshV1Cipher(trimmed) != "none"
            else -> false
        }
    }

    /**
     * Load [text], decrypting with [passphrase] when it is protected, and return the
     * key pair with its derived OpenSSH public-key line.
     *
     * @throws KeyMaterialException on a malformed key, an unsupported format, or a
     *   wrong passphrase — the three cases the UI has to explain to the user.
     */
    fun load(text: String, passphrase: String? = null, comment: String = ""): KeyMaterial {
        SshSecurity.install()
        val normalized = text.trim() + "\n"
        val publicKey = try {
            val provider = providerFor(normalized)
            provider.init(
                StringReader(normalized),
                passphrase?.let { PasswordUtils.createOneOff(it.toCharArray()) },
            )
            provider.public
        } catch (e: Exception) {
            throw KeyMaterialException(failureMessage(normalized, passphrase), e)
        } ?: throw KeyMaterialException("The key holds no public half")

        val line = OpenSshFormat.publicKeyLine(
            Buffer.PlainBuffer().putPublicKey(publicKey).compactData,
            comment,
        )
        val type = OpenSshFormat.typeOf(line)
            ?: throw KeyMaterialException("Unsupported key algorithm: ${line.substringBefore(' ')}")
        return KeyMaterial(type, normalized, line, encrypted = isEncrypted(normalized))
    }

    private fun providerFor(text: String): FileKeyProvider {
        val format = KeyProviderUtil.detectKeyFileFormat(text, false)
        return Factory.Named.Util.create(DefaultConfig().fileKeyProviderFactories, format.toString())
            ?: throw KeyMaterialException("Unsupported private key format: $format")
    }

    private fun failureMessage(text: String, passphrase: String?): String = when {
        !isEncrypted(text) -> "That does not look like a valid private key"
        passphrase.isNullOrEmpty() -> "This key is passphrase-protected"
        else -> "Wrong passphrase"
    }

    /**
     * The cipher name in an OpenSSH v1 block: after the NUL-terminated `openssh-key-v1`
     * magic comes a length-prefixed cipher name, `none` for an unprotected key.
     */
    private fun openSshV1Cipher(text: String): String {
        val body = text.substringAfter("BEGIN OPENSSH PRIVATE KEY-----")
            .substringBefore("-----END")
            .filterNot { it.isWhitespace() }
        val der = runCatching { Base64.getDecoder().decode(body) }.getOrNull() ?: return "none"
        val magic = "openssh-key-v1\u0000".toByteArray(Charsets.US_ASCII)
        if (der.size < magic.size + 4) return "none"
        return runCatching {
            Buffer.PlainBuffer(der.copyOfRange(magic.size, der.size)).readString()
        }.getOrDefault("none")
    }
}
