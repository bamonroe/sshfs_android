package com.bam.sshfs.crypto

import com.bam.sshfs.data.model.KeyOrigin
import com.bam.sshfs.data.model.KeyType
import com.bam.sshfs.data.model.SshKey
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyExportTest {

    private fun key(name: String, type: KeyType = KeyType.ED25519) = SshKey(
        name = name,
        type = type,
        privateKeyCiphertext = "x",
        publicKey = "ssh-ed25519 AAAA",
        origin = KeyOrigin.GENERATED,
        createdAt = 0,
    )

    @Test
    fun `the file name follows ssh-keygen's convention for the algorithm`() {
        assertEquals("id_ed25519_laptop", KeyExport.suggestedFileName(key("laptop")))
        assertEquals("id_rsa_laptop", KeyExport.suggestedFileName(key("laptop", KeyType.RSA)))
        assertEquals("id_ecdsa_laptop", KeyExport.suggestedFileName(key("laptop", KeyType.ECDSA)))
    }

    @Test
    fun `awkward characters in the name become underscores`() {
        assertEquals("id_ed25519_work_box_2", KeyExport.suggestedFileName(key("Work box/2")))
    }

    @Test
    fun `a name with nothing usable falls back to the bare stem`() {
        assertEquals("id_ed25519", KeyExport.suggestedFileName(key(" -- ")))
    }

    @Test
    fun `the file is the stored key with exactly one trailing newline`() {
        assertEquals("-----BEGIN-----\n", KeyExport.fileContents("-----BEGIN-----"))
        assertEquals("-----BEGIN-----\n", KeyExport.fileContents("-----BEGIN-----\n\n"))
    }
}
