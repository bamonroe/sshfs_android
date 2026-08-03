package com.bam.sshfs.net.ssh

import java.security.KeyPairGenerator
import java.security.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KnownHostsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val key: PublicKey by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
    }

    private val otherKey: PublicKey by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
    }

    @Test
    fun `the file store survives a round trip through disk`() {
        val file = folder.newFile("known_hosts")
        FileKnownHostsStore(file).remember(KnownHost("example.com", 2222, "SHA256:abc"))

        val reopened = FileKnownHostsStore(file)
        assertEquals("SHA256:abc", reopened.lookup("example.com", 2222)?.fingerprint)
        assertNull("a different port is a different host", reopened.lookup("example.com", 22))
    }

    @Test
    fun `forgetting a host removes it from the file`() {
        val file = folder.newFile("known_hosts")
        val store = FileKnownHostsStore(file)
        store.remember(KnownHost("example.com", 22, "SHA256:abc"))
        store.forget("example.com", 22)

        assertTrue(FileKnownHostsStore(file).all().isEmpty())
    }

    @Test
    fun `blank and malformed lines are ignored`() {
        val file = folder.newFile("known_hosts")
        file.writeText("\n# comment\ngarbage\nexample.com:22 SHA256:abc\n")

        assertEquals(1, FileKnownHostsStore(file).all().size)
    }

    @Test
    fun `first contact is trusted and remembered`() {
        val store = InMemoryKnownHostsStore()
        val verifier = TofuHostKeyVerifier(store, "example.com", 22)

        assertTrue(verifier.verify("example.com", 22, key))
        assertEquals(fingerprintOf(key), store.lookup("example.com", 22)?.fingerprint)
    }

    @Test
    fun `first contact is refused when strict checking is on`() {
        val store = InMemoryKnownHostsStore()
        val verifier = TofuHostKeyVerifier(store, "example.com", 22, strict = true)

        assertFalse(verifier.verify("example.com", 22, key))
        assertEquals(SshFailure.HOST_KEY_UNKNOWN, verifier.rejection?.failure)
        assertTrue("an unknown key must not be remembered", store.all().isEmpty())
    }

    @Test
    fun `a changed host key is always refused`() {
        val store = InMemoryKnownHostsStore(
            listOf(KnownHost("example.com", 22, fingerprintOf(key)))
        )
        val verifier = TofuHostKeyVerifier(store, "example.com", 22)

        assertFalse(verifier.verify("example.com", 22, otherKey))
        assertEquals(SshFailure.HOST_KEY_CHANGED, verifier.rejection?.failure)
        assertEquals(
            "the remembered key must be left alone",
            fingerprintOf(key),
            store.lookup("example.com", 22)?.fingerprint,
        )
    }

    @Test
    fun `a matching host key is accepted`() {
        val store = InMemoryKnownHostsStore(
            listOf(KnownHost("example.com", 22, fingerprintOf(key)))
        )
        assertTrue(TofuHostKeyVerifier(store, "example.com", 22).verify("example.com", 22, key))
    }

    @Test
    fun `fingerprints use the OpenSSH SHA256 spelling`() {
        assertTrue(fingerprintOf(key).startsWith("SHA256:"))
        assertFalse("base64 is unpadded", fingerprintOf(key).endsWith("="))
    }
}
