package com.bam.sshfs.crypto

import com.bam.sshfs.data.model.KeyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Import behaviour the dialog depends on: encryption detection and clear failures. */
class KeyImporterTest {

    companion object {
        @BeforeClass @JvmStatic fun installProvider() = SshSecurity.install()
    }

    @Test
    fun `an unprotected generated key is not reported as encrypted`() {
        val generated = KeyPairFactory.generate(KeyType.ED25519, "x")
        assertFalse(KeyImporter.isEncrypted(generated.privateKey))
    }

    @Test
    fun `a legacy pem marked Proc-Type is reported as encrypted`() {
        val pem = """
            -----BEGIN RSA PRIVATE KEY-----
            Proc-Type: 4,ENCRYPTED
            DEK-Info: AES-128-CBC,0123456789ABCDEF

            AAAA
            -----END RSA PRIVATE KEY-----
        """.trimIndent()
        assertTrue(KeyImporter.isEncrypted(pem))
    }

    @Test
    fun `garbage input fails with a message the dialog can show`() {
        val failure = runCatching { KeyImporter.load("not a key at all") }.exceptionOrNull()
        assertTrue(failure is KeyMaterialException)
        assertEquals("That does not look like a valid private key", failure?.message)
    }

    @Test
    fun `the comment is attached to the derived public key`() {
        val generated = KeyPairFactory.generate(KeyType.ED25519, "ignored")
        val imported = KeyImporter.load(generated.privateKey, comment = "work laptop")
        assertTrue(imported.publicKey.endsWith(" work laptop"))
        assertEquals(KeyType.ED25519, imported.type)
    }
}
