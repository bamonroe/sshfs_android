package com.bam.sshfs.crypto

import com.bam.sshfs.data.model.KeyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Generated pairs must round-trip through the same importer the UI feeds pasted
 * keys to — if SSHJ can't read what we generate, neither can the transport.
 */
class KeyPairFactoryTest {

    companion object {
        @BeforeClass @JvmStatic fun installProvider() = SshSecurity.install()
    }

    @Test
    fun `ed25519 pair is an openssh block that reimports identically`() {
        val generated = KeyPairFactory.generate(KeyType.ED25519, "phone")

        assertEquals(KeyType.ED25519, generated.type)
        assertTrue(generated.privateKey.contains("BEGIN OPENSSH PRIVATE KEY"))
        assertTrue(generated.publicKey.startsWith("ssh-ed25519 "))
        assertTrue(generated.publicKey.endsWith(" phone"))
        assertFalse(generated.encrypted)

        val reimported = KeyImporter.load(generated.privateKey, comment = "phone")
        assertEquals(generated.publicKey, reimported.publicKey)
    }

    @Test
    fun `rsa pair is a pkcs1 pem that reimports identically`() {
        val generated = KeyPairFactory.generate(KeyType.RSA, "phone")

        assertEquals(KeyType.RSA, generated.type)
        assertTrue(generated.privateKey.contains("BEGIN RSA PRIVATE KEY"))
        assertTrue(generated.publicKey.startsWith("ssh-rsa "))

        val reimported = KeyImporter.load(generated.privateKey, comment = "phone")
        assertEquals(generated.publicKey, reimported.publicKey)
    }

    @Test
    fun `each generated pair is distinct`() {
        val a = KeyPairFactory.generate(KeyType.ED25519, "a")
        val b = KeyPairFactory.generate(KeyType.ED25519, "b")
        assertNotNull(OpenSshFormat.fingerprint(a.publicKey))
        assertTrue(OpenSshFormat.fingerprint(a.publicKey) != OpenSshFormat.fingerprint(b.publicKey))
    }

    @Test(expected = KeyMaterialException::class)
    fun `ecdsa generation is refused`() {
        KeyPairFactory.generate(KeyType.ECDSA, "x")
    }
}
