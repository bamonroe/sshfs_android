package com.bam.sshfs.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentIdTest {

    @Test
    fun `round-trips through its wire form`() {
        val id = DocumentId(7, "/home/bam/notes.txt")
        assertEquals(id, DocumentId.parse(id.toString()))
    }

    @Test
    fun `keeps a colon inside the remote path`() {
        val parsed = DocumentId.parse("3:/srv/a:b/c")
        assertEquals(3L, parsed.hostId)
        assertEquals("/srv/a:b/c", parsed.path)
    }

    @Test
    fun `child joins onto the parent path`() {
        assertEquals("/home/bam", DocumentId(1, "/home").child("bam").path)
        assertEquals("/bam", DocumentId(1, "/").child("bam").path)
    }

    @Test
    fun `parent walks up and stops at the root`() {
        assertEquals("/home", DocumentId(1, "/home/bam").parent()?.path)
        assertNull(DocumentId(1, "/").parent())
    }

    @Test
    fun `contains only descendants on the same host`() {
        val parent = DocumentId(1, "/home")
        assertTrue(parent.contains(DocumentId(1, "/home/bam/notes.txt")))
        assertFalse(parent.contains(DocumentId(2, "/home/bam")))
        assertFalse(parent.contains(DocumentId(1, "/home")))
        assertFalse(parent.contains(DocumentId(1, "/homework/x")))
    }

    @Test
    fun `rejects malformed ids`() {
        for (bad in listOf("", "/home", "abc:/home", ":/home", "1:home")) {
            assertThrows(IllegalArgumentException::class.java) { DocumentId.parse(bad) }
        }
    }
}
