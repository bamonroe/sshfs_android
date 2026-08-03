package com.bam.sshfs.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** Round-trips secrets against the device's real `AndroidKeyStore`. */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreInstrumentedTest {

    private val alias = "com.bam.sshfs.test.secrets"
    private val store = KeystoreSecretStore(alias)

    @After
    fun dropTestKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun roundTripsASecret() {
        val secret = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n"
        assertEquals(secret, store.decrypt(store.encrypt(secret)))
    }

    @Test
    fun ciphertextHidesThePlaintextAndIsSaltedPerCall() {
        val first = store.encrypt("hunter2")
        val second = store.encrypt("hunter2")
        assertTrue(first.startsWith("v1:"))
        assertTrue(!first.contains("hunter2"))
        // A fresh IV per call, so identical passwords don't produce identical rows.
        assertNotEquals(first, second)
    }

    @Test
    fun rejectsATamperedBlob() {
        val sealed = store.encrypt("hunter2")
        val flipped = sealed.dropLast(2) + if (sealed.endsWith("A=")) "B=" else "A="
        val e = runCatching { store.decrypt(flipped) }.exceptionOrNull()
        assertTrue(e is SecretStoreException)
    }
}
