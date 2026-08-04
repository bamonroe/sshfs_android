package com.bam.sshfs.backup

import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.KeyOrigin
import com.bam.sshfs.data.model.KeyType
import com.bam.sshfs.data.model.SshKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The properties the restore's dedupe relies on. */
class BackupHashesTest {

    private fun key(name: String = "laptop", pub: String = "ssh-ed25519 AAAA") = BackupKey(
        id = 7, name = name, type = KeyType.ED25519, privateKey = "PRIVATE", publicKey = pub,
        hasPassphrase = true, passphrase = "pp", origin = KeyOrigin.GENERATED, createdAt = 1,
    )

    private fun row(name: String = "laptop", pub: String = "ssh-ed25519 AAAA") = SshKey(
        id = 42, name = name, type = KeyType.ED25519, privateKeyCiphertext = "sealed",
        publicKey = pub, hasPassphrase = true, passphraseCiphertext = "sealed",
        origin = KeyOrigin.GENERATED, createdAt = 900,
    )

    @Test
    fun `a document key and the row it came from hash alike`() {
        // Different ids and different createdAt: neither is content.
        assertEquals(BackupHashes.key(key()), BackupHashes.key(row()))
    }

    @Test
    fun `the secret material is not part of the hash`() {
        assertEquals(
            BackupHashes.key(key()),
            BackupHashes.key(key().copy(privateKey = "", passphrase = null)),
        )
    }

    @Test
    fun `a different public half is a different key`() {
        assertNotEquals(BackupHashes.key(key()), BackupHashes.key(key(pub = "ssh-ed25519 BBBB")))
    }

    @Test
    fun `a rename is a different element`() {
        assertNotEquals(BackupHashes.key(key()), BackupHashes.key(key(name = "desktop")))
    }

    @Test
    fun `an identity commits to the key it points at`() {
        val identity = BackupIdentity(1, "me", "bam", "pw", 7, 2)
        assertNotEquals(
            BackupHashes.identity(identity, BackupHashes.key(key())),
            BackupHashes.identity(identity, null),
        )
        // The reference travels as a hash, so the local row with a remapped key id matches.
        assertEquals(
            BackupHashes.identity(identity, BackupHashes.key(key())),
            BackupHashes.identity(Identity(99, "me", "bam", "sealed", 5, 900), BackupHashes.key(row())),
        )
    }

    @Test
    fun `a host commits to its default identity`() {
        val host = BackupHost(1, "nas", "nas.local", 2222, 3, "/srv", "", 3)
        assertNotEquals(BackupHashes.host(host, "aaa"), BackupHashes.host(host, "bbb"))
        assertEquals(
            BackupHashes.host(host, null),
            BackupHashes.host(Host(8, "nas", "nas.local", 2222, null, "/srv", "", 77), null),
        )
    }

    @Test
    fun `field boundaries cannot be shifted`() {
        val host = BackupHost(1, "na", "s.local", 2222, null, ".", "", 3)
        val shifted = host.copy(name = "nas", address = ".local")
        assertNotEquals(BackupHashes.host(host, null), BackupHashes.host(shifted, null))
    }

    @Test
    fun `the encoded file records every hash`() {
        val document = BackupDocument(
            createdAt = 1,
            keys = listOf(key()),
            identities = listOf(BackupIdentity(1, "me", "bam", null, 7, 2)),
            hosts = listOf(BackupHost(1, "nas", "nas.local", 22, 1, ".", "", 3)),
        )
        val text = BackupJson.encode(document)
        BackupHashes.of(document).let { hashes ->
            (hashes.keys.values + hashes.identities.values + hashes.hosts.values).forEach {
                assertTrue("missing $it", text.contains(it))
            }
        }
        // A recorded hash is decoration, not input: decode drops it and the restore
        // recomputes, so a hand-edited file can't lie its way past the dedupe.
        assertEquals(document, BackupJson.decode(text))
    }
}
