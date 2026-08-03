package com.bam.sshfs.crypto

import com.bam.sshfs.data.model.KeyType
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import java.math.BigInteger
import java.security.SecureRandom

/**
 * Generates key pairs on-device, in the same text forms `ssh-keygen` writes:
 * Ed25519 as an OpenSSH v1 block, RSA as a PKCS#1 PEM.
 *
 * Generated keys carry no passphrase — at rest they are protected by the
 * Keystore-backed [SecretStore] instead, which is what actually guards the
 * database file.
 */
object KeyPairFactory {

    /** Key sizes we generate; anything smaller is not worth offering. */
    const val RSA_BITS = 3072
    private const val RSA_CERTAINTY = 100
    private val RSA_EXPONENT = BigInteger.valueOf(65537)

    /** The algorithms the UI offers for on-device generation. */
    val GENERATABLE = listOf(KeyType.ED25519, KeyType.RSA)

    fun generate(type: KeyType, comment: String, random: SecureRandom = SecureRandom()): KeyMaterial =
        when (type) {
            KeyType.ED25519 -> ed25519(comment, random)
            KeyType.RSA -> rsa(comment, random)
            KeyType.ECDSA -> throw KeyMaterialException(
                "ECDSA keys can be imported but are not generated on-device",
            )
        }

    private fun ed25519(comment: String, random: SecureRandom): KeyMaterial {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(random))
        return materialize(generator.generateKeyPair(), KeyType.ED25519, "OPENSSH PRIVATE KEY", comment)
    }

    private fun rsa(comment: String, random: SecureRandom): KeyMaterial {
        val generator = RSAKeyPairGenerator()
        generator.init(RSAKeyGenerationParameters(RSA_EXPONENT, random, RSA_BITS, RSA_CERTAINTY))
        return materialize(generator.generateKeyPair(), KeyType.RSA, "RSA PRIVATE KEY", comment)
    }

    private fun materialize(
        pair: AsymmetricCipherKeyPair,
        type: KeyType,
        pemLabel: String,
        comment: String,
    ): KeyMaterial = KeyMaterial(
        type = type,
        privateKey = OpenSshFormat.pem(pemLabel, OpenSSHPrivateKeyUtil.encodePrivateKey(pair.private)),
        publicKey = OpenSshFormat.publicKeyLine(pair.public, comment),
    )
}
