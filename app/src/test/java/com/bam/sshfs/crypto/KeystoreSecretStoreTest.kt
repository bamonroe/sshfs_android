package com.bam.sshfs.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * The blob-format half of [KeystoreSecretStore] — the paths that resolve before any
 * `AndroidKeyStore` call, which a desktop JVM has no provider for. The round-trip
 * against a real Keystore lives in the instrumented test of the same name.
 */
class KeystoreSecretStoreTest {

    private val store = KeystoreSecretStore()

    @Test
    fun `reads back an unprefixed blob as pre-encryption Base64`() {
        val legacy = Base64.getEncoder().encodeToString("hunter2".toByteArray())
        assertEquals("hunter2", store.decrypt(legacy))
    }

    @Test
    fun `rejects a v1 blob that is not Base64`() {
        val e = runCatching { store.decrypt("v1:not base64!") }.exceptionOrNull()
        assertTrue(e is SecretStoreException)
    }

    @Test
    fun `rejects a v1 blob too short to hold an IV`() {
        val short = "v1:" + Base64.getEncoder().encodeToString(ByteArray(8))
        val e = runCatching { store.decrypt(short) }.exceptionOrNull()
        assertTrue(e is SecretStoreException)
    }
}
