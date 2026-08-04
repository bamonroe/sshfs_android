package com.bam.sshfs.backup

import com.bam.sshfs.crypto.PassthroughSecretStore
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.KeyOrigin
import com.bam.sshfs.data.model.KeyType
import com.bam.sshfs.data.model.SshKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Export → seal → open → restore, against in-memory DAOs: the path a user's backup
 * actually takes, minus the Keystore and the file picker.
 */
class BackupRoundTripTest {

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
    fun `restores into an empty install with the links intact`() = runBlocking {
        val (keys, identities, hosts) = source()
        val file = BackupCrypto.seal(
            BackupJson.encode(
                BackupExporter(keys, identities, hosts, secrets).collect(now = 99),
            ),
            "pw",
        )

        val (freshKeys, freshIdentities, freshHosts) = Triple(
            FakeKeyDao(), FakeIdentityDao(), FakeHostDao(),
        )
        val result = BackupRestorer(freshKeys, freshIdentities, freshHosts, secrets)
            .restore(BackupJson.decode(BackupCrypto.open(file, "pw")))

        assertEquals(RestoreResult(1, 1, 1), result)
        val key = freshKeys.all().single()
        assertEquals("PRIVATE", secrets.decrypt(key.privateKeyCiphertext))
        assertEquals("hunter2", secrets.decrypt(key.passphraseCiphertext!!))
        val identity = freshIdentities.all().single()
        assertEquals(key.id, identity.keyId)
        assertEquals("s3cret", secrets.decrypt(identity.passwordCiphertext!!))
        val host = freshHosts.all().single()
        assertEquals(identity.id, host.defaultIdentityId)
        assertEquals(2222, host.port)
        assertEquals("Compression yes", host.extraArgs)
    }

    @Test
    fun `merges into an install that already has the same names`() = runBlocking {
        val (keys, identities, hosts) = source()
        val document = BackupExporter(keys, identities, hosts, secrets).collect(now = 99)

        // Restore the backup back over its own source: nothing is replaced, and the
        // duplicate names are disambiguated rather than colliding.
        val result = BackupRestorer(keys, identities, hosts, secrets).restore(document)

        assertEquals(RestoreResult(1, 1, 1), result)
        assertEquals(listOf("laptop", "laptop (2)"), keys.all().map { it.name })
        assertEquals(2, hosts.all().size)
        val restoredIdentity = identities.all().last()
        assertNotNull(restoredIdentity.keyId)
        // The copy points at the *new* key, not at the original it was exported from.
        assertEquals(keys.all().last().id, restoredIdentity.keyId)
    }

    @Test
    fun `an unopenable backup changes nothing`() = runBlocking {
        val (keys, identities, hosts) = source()
        val file = BackupCrypto.seal(
            BackupJson.encode(BackupExporter(keys, identities, hosts, secrets).collect(now = 1)),
            "right",
        )
        val failed = runCatching { BackupCrypto.open(file, "wrong") }.exceptionOrNull()
        assertEquals(WrongPassphraseException::class.java, failed?.javaClass)
        assertEquals(1, keys.all().size)
    }
}
