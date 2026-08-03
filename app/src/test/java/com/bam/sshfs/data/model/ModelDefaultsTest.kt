package com.bam.sshfs.data.model

import com.bam.sshfs.data.db.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The defaults and enum encoding the schema depends on.
 *
 * These look trivial, but they are exactly the values a migration or a careless
 * refactor silently changes: the port a blank field falls back to, the root a new
 * host opens at, and the *names* the enums are stored under — switching those to
 * ordinals would quietly repoint every stored key at the wrong type.
 */
class ModelDefaultsTest {

    private val converters = Converters()

    @Test
    fun aNewHostDefaultsToPort22AtTheLoginDirectory() {
        val host = Host(name = "box", address = "example.com", createdAt = 0)

        assertEquals(DEFAULT_SSH_PORT, host.port)
        assertEquals(22, host.port)
        assertEquals(".", host.remoteRoot)
        assertEquals("", host.extraArgs)
        assertNull(host.defaultIdentityId)
    }

    @Test
    fun anIdentityCarriesNeitherSecretByDefault() {
        val identity = Identity(name = "me", username = "root", createdAt = 0)

        assertNull(identity.passwordCiphertext)
        assertNull(identity.keyId)
    }

    @Test
    fun aKeyIsPassphraselessByDefault() {
        val key = SshKey(
            name = "id_ed25519",
            type = KeyType.ED25519,
            privateKeyCiphertext = "x",
            publicKey = "ssh-ed25519 AAAA",
            origin = KeyOrigin.GENERATED,
            createdAt = 0,
        )

        assertEquals(false, key.hasPassphrase)
        assertNull(key.passphraseCiphertext)
    }

    @Test
    fun enumsAreStoredByName() {
        for (type in KeyType.entries) {
            assertEquals(type, converters.stringToKeyType(converters.keyTypeToString(type)))
        }
        for (origin in KeyOrigin.entries) {
            assertEquals(origin, converters.stringToKeyOrigin(converters.keyOriginToString(origin)))
        }
        assertEquals("ED25519", converters.keyTypeToString(KeyType.ED25519))
        assertEquals("IMPORTED", converters.keyOriginToString(KeyOrigin.IMPORTED))
    }

    @Test
    fun anUnknownStoredEnumNameIsAnError() {
        assertThrows(IllegalArgumentException::class.java) { converters.stringToKeyType("DSA") }
    }
}
