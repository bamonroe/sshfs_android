package com.bam.sshfs.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The passphrase envelope: a round trip, and every way it is meant to refuse. */
class BackupCryptoTest {

    @Test
    fun `round trips a document`() {
        val sealed = BackupCrypto.seal(PLAINTEXT, "correct horse battery staple")
        assertEquals(PLAINTEXT, BackupCrypto.open(sealed, "correct horse battery staple"))
    }

    @Test
    fun `sealed text never contains the plaintext`() {
        val sealed = BackupCrypto.seal(PLAINTEXT, "pw")
        assertFalse(sealed.contains("hunter2"))
        assertTrue(BackupCrypto.isSealed(sealed))
    }

    @Test
    fun `two seals of the same input differ`() {
        // A fresh salt and IV each time, so identical backups aren't recognisable.
        assertFalse(BackupCrypto.seal(PLAINTEXT, "pw") == BackupCrypto.seal(PLAINTEXT, "pw"))
    }

    @Test(expected = WrongPassphraseException::class)
    fun `rejects the wrong passphrase`() {
        BackupCrypto.open(BackupCrypto.seal(PLAINTEXT, "right"), "wrong")
    }

    @Test(expected = WrongPassphraseException::class)
    fun `rejects a tampered ciphertext`() {
        val sealed = BackupCrypto.seal(PLAINTEXT, "pw")
        // Swap one base64 character for another, so the field still decodes and it is
        // the GCM tag — not the parser — that catches the change.
        val tampered = sealed.lines().joinToString("\n") { line ->
            if (!line.startsWith("data: ")) {
                line
            } else {
                val body = line.removePrefix("data: ")
                val at = body.length / 2
                body.replaceRange(at, at + 1, if (body[at] == 'A') "B" else "A")
                    .let { "data: $it" }
            }
        }
        BackupCrypto.open(tampered, "pw")
    }

    @Test(expected = BackupFormatException::class)
    fun `rejects a file that is not a backup`() {
        BackupCrypto.open("just some text\n", "pw")
    }

    @Test(expected = BackupFormatException::class)
    fun `rejects a backup missing a field`() {
        val sealed = BackupCrypto.seal(PLAINTEXT, "pw")
        BackupCrypto.open(sealed.lines().filterNot { it.startsWith("salt:") }.joinToString("\n"), "pw")
    }

    @Test
    fun `refuses an empty passphrase`() {
        val failed = runCatching { BackupCrypto.seal(PLAINTEXT, "") }.isFailure
        assertTrue(failed)
    }

    private companion object {
        const val PLAINTEXT = """{"secret":"hunter2"}"""
    }
}
