package com.bam.sshfs.backup

import com.bam.sshfs.data.model.KeyOrigin
import com.bam.sshfs.data.model.KeyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The backup document's encoding: a full round trip, and its tolerances. */
class BackupJsonTest {

    private val document = BackupDocument(
        createdAt = 1_700_000_000_000,
        keys = listOf(
            BackupKey(
                id = 1,
                name = "laptop",
                type = KeyType.ED25519,
                privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n",
                publicKey = "ssh-ed25519 AAAA laptop",
                hasPassphrase = true,
                passphrase = "hunter2",
                origin = KeyOrigin.GENERATED,
                createdAt = 5,
            ),
        ),
        identities = listOf(BackupIdentity(2, "me", "bam", "pw", 1, 6)),
        hosts = listOf(BackupHost(3, "nas", "nas.local", 2222, 2, "/srv", "Compression yes", 7)),
    )

    @Test
    fun `round trips every field`() {
        val decoded = BackupJson.decode(BackupJson.encode(document))
        assertEquals(document, decoded)
    }

    @Test
    fun `keeps null secrets null`() {
        val bare = document.copy(
            keys = listOf(document.keys[0].copy(passphrase = null, hasPassphrase = false)),
            identities = listOf(document.identities[0].copy(password = null, keyId = null)),
            hosts = listOf(document.hosts[0].copy(defaultIdentityId = null)),
        )
        val decoded = BackupJson.decode(BackupJson.encode(bare))
        assertNull(decoded.keys[0].passphrase)
        assertNull(decoded.identities[0].password)
        assertNull(decoded.identities[0].keyId)
        assertNull(decoded.hosts[0].defaultIdentityId)
    }

    @Test
    fun `fills in absent optional fields`() {
        val decoded = BackupJson.decode(
            """{"version":1,"hosts":[{"id":1,"name":"nas"}]}""",
        )
        assertTrue(decoded.keys.isEmpty())
        assertEquals(22, decoded.hosts[0].port)
        assertEquals(".", decoded.hosts[0].remoteRoot)
    }

    @Test(expected = BackupFormatException::class)
    fun `refuses a newer format version`() {
        BackupJson.decode("""{"version":99}""")
    }

    @Test(expected = BackupFormatException::class)
    fun `refuses text that is not json`() {
        BackupJson.decode("nope")
    }

    @Test(expected = BackupFormatException::class)
    fun `refuses a row with no id`() {
        BackupJson.decode("""{"version":1,"hosts":[{"name":"nas"}]}""")
    }
}
