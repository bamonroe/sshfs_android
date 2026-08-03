package com.bam.sshfs.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentModeTest {

    @Test
    fun `read only mode opens nothing writable`() {
        val mode = DocumentMode.parse("r")
        assertTrue(mode.read)
        assertFalse(mode.write)
        assertFalse(mode.truncate)
        assertTrue(mode.readOnly)
    }

    @Test
    fun `bare write truncates so a shorter rewrite leaves no tail`() {
        val mode = DocumentMode.parse("w")
        assertTrue(mode.write)
        assertTrue(mode.truncate)
    }

    @Test
    fun `append keeps the existing contents`() {
        val mode = DocumentMode.parse("wa")
        assertTrue(mode.write)
        assertTrue(mode.append)
        assertFalse(mode.truncate)
    }

    @Test
    fun `read-write does not truncate unless asked`() {
        assertFalse(DocumentMode.parse("rw").truncate)
        assertTrue(DocumentMode.parse("rwt").truncate)
    }

    @Test
    fun `unsupported characters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DocumentMode.parse("x") }
        assertThrows(IllegalArgumentException::class.java) { DocumentMode.parse("") }
        assertThrows(IllegalArgumentException::class.java) { DocumentMode.parse("t") }
    }

    @Test
    fun `mode characters may arrive in any order`() {
        assertEquals(DocumentMode.parse("rwt"), DocumentMode.parse("wtr"))
    }
}
