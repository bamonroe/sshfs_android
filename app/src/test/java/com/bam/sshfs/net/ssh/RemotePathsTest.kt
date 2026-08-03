package com.bam.sshfs.net.ssh

import com.bam.sshfs.crypto.PassthroughSecretStore
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.KeyOrigin
import com.bam.sshfs.data.model.KeyType
import com.bam.sshfs.data.model.SshKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePathsTest {

    @Test
    fun `join collapses the separator`() {
        assertEquals("/home/bam/file", RemotePaths.join("/home/bam", "file"))
        assertEquals("/home/bam/file", RemotePaths.join("/home/bam/", "/file"))
        assertEquals("/file", RemotePaths.join("/", "file"))
    }

    @Test
    fun `name and parent walk the tree`() {
        assertEquals("file", RemotePaths.name("/home/bam/file"))
        assertEquals("/home/bam", RemotePaths.parent("/home/bam/file"))
        assertEquals("/", RemotePaths.parent("/home"))
        assertNull(RemotePaths.parent("/"))
    }
}

class CredentialResolverTest {

    private val secrets = PassthroughSecretStore()
    private val resolver = CredentialResolver(secrets)

    @Test
    fun `decrypts the password and the key together`() {
        val identity = Identity(
            name = "work",
            username = " bam ",
            passwordCiphertext = secrets.encrypt("hunter2"),
            createdAt = 0,
        )
        val key = SshKey(
            name = "laptop",
            type = KeyType.ED25519,
            privateKeyCiphertext = secrets.encrypt("PRIVATE KEY"),
            publicKey = "ssh-ed25519 AAAA",
            hasPassphrase = true,
            passphraseCiphertext = secrets.encrypt("open sesame"),
            origin = KeyOrigin.GENERATED,
            createdAt = 0,
        )

        val credentials = resolver.resolve(identity, key)

        assertEquals("bam", credentials.username)
        assertEquals("hunter2", credentials.password)
        assertEquals("PRIVATE KEY", credentials.privateKey)
        assertEquals("open sesame", credentials.passphrase)
        assertTrue(credentials.hasCredential)
    }

    @Test
    fun `an identity with neither credential cannot connect`() {
        val identity = Identity(name = "empty", username = "bam", createdAt = 0)
        assertFalse(resolver.resolve(identity, null).hasCredential)
    }
}
