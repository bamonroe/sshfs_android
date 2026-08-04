package com.bam.sshfs.backup

import com.bam.sshfs.crypto.PassthroughSecretStore
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.KeyOrigin
import com.bam.sshfs.data.model.KeyType
import com.bam.sshfs.data.model.SshKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The unencrypted config-only export: everything about the setup, none of the secrets,
 * and a restore that leaves the keys as fillable placeholders.
 */
class ConfigOnlyTest {

    private val secrets = PassthroughSecretStore()

    private fun source(): Triple<FakeKeyDao, FakeIdentityDao, FakeHostDao> = runBlocking {
        val keys = FakeKeyDao()
        val identities = FakeIdentityDao()
        val hosts = FakeHostDao()
        val keyId = keys.insert(
            SshKey(
                name = "laptop",
                type = KeyType.ED25519,
                privateKeyCiphertext = secrets.encrypt("PRIVATE"),
                publicKey = "ssh-ed25519 AAAA laptop",
                hasPassphrase = true,
                passphraseCiphertext = secrets.encrypt("hunter2"),
                origin = KeyOrigin.GENERATED,
                createdAt = 1,
            ),
        )
        val identityId = identities.insert(
            Identity(
                name = "me",
                username = "bam",
                passwordCiphertext = secrets.encrypt("s3cret"),
                keyId = keyId,
                createdAt = 2,
            ),
        )
        hosts.insert(
            Host(
                name = "nas",
                address = "nas.local",
                port = 2222,
                defaultIdentityId = identityId,
                remoteRoot = "/srv",
                extraArgs = "Compression yes",
                createdAt = 3,
            ),
        )
        Triple(keys, identities, hosts)
    }

    @Test
    fun `the exported file carries no secret material`() = runBlocking {
        val (keys, identities, hosts) = source()
        val text = BackupJson.encode(
            BackupExporter(keys, identities, hosts, secrets).collectConfigOnly(now = 99),
        )

        // The whole point: this file is meant to be readable and shareable.
        assertFalse(BackupCrypto.isSealed(text))
        assertFalse(text.contains("PRIVATE"))
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("s3cret"))
        // …but the configuration around the secrets survives.
        assertTrue(text.contains("nas.local"))
        assertTrue(text.contains("ssh-ed25519 AAAA laptop"))
        assertTrue(ConfigOnly.isRedacted(BackupJson.decode(text)))
    }

    @Test
    fun `restores hosts and links, leaving the key as a placeholder`() = runBlocking {
        val (keys, identities, hosts) = source()
        val document = BackupJson.decode(
            BackupJson.encode(
                BackupExporter(keys, identities, hosts, secrets).collectConfigOnly(now = 99),
            ),
        )

        val (freshKeys, freshIdentities, freshHosts) = Triple(
            FakeKeyDao(), FakeIdentityDao(), FakeHostDao(),
        )
        val result = BackupRestorer(freshKeys, freshIdentities, freshHosts, secrets)
            .restore(document)

        assertEquals(RestoreResult(1, 1, 1, listOf("laptop")), result)
        val key = freshKeys.all().single()
        assertFalse(key.hasPrivateHalf)
        // The public half and the algorithm come back, so the key is still identifiable.
        assertEquals("ssh-ed25519 AAAA laptop", key.publicKey)
        assertEquals(KeyType.ED25519, key.type)
        val identity = freshIdentities.all().single()
        assertNull(identity.passwordCiphertext)
        // The link is the reason placeholders exist at all: the host still knows which
        // key it wants, it just can't use it yet.
        assertEquals(key.id, identity.keyId)
        assertEquals(identity.id, freshHosts.all().single().defaultIdentityId)
    }

    @Test
    fun `a full backup restores with no placeholders`() = runBlocking {
        val (keys, identities, hosts) = source()
        val document = BackupExporter(keys, identities, hosts, secrets).collect(now = 99)
        val (freshKeys, freshIdentities, freshHosts) = Triple(
            FakeKeyDao(), FakeIdentityDao(), FakeHostDao(),
        )

        val result = BackupRestorer(freshKeys, freshIdentities, freshHosts, secrets)
            .restore(document)

        assertEquals(emptyList<String>(), result.incompleteKeys)
        assertTrue(freshKeys.all().single().hasPrivateHalf)
    }

    @Test
    fun `redaction is idempotent and leaves the configuration alone`() {
        val document = BackupDocument(
            createdAt = 1,
            keys = listOf(
                BackupKey(
                    id = 1, name = "k", type = KeyType.RSA, privateKey = "PRIVATE",
                    publicKey = "pub", hasPassphrase = true, passphrase = "pp",
                    origin = KeyOrigin.IMPORTED, createdAt = 1,
                ),
            ),
            identities = listOf(BackupIdentity(1, "i", "bam", "pw", 1, 1)),
            hosts = listOf(BackupHost(1, "h", "example.com", 22, 1, ".", "", 1)),
        )

        val once = ConfigOnly.redact(document)
        assertEquals(once, ConfigOnly.redact(once))
        assertEquals(document.hosts, once.hosts)
        // hasPassphrase is kept: it describes the key, and tells the user the material
        // they have to supply will need one.
        assertTrue(once.keys.single().hasPassphrase)
    }
}
