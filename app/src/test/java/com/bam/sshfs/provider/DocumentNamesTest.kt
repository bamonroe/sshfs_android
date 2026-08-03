package com.bam.sshfs.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentNamesTest {

    @Test
    fun `a free name is used unchanged`() {
        assertEquals("notes.txt", DocumentNames.unique("notes.txt", setOf("other.txt")))
    }

    @Test
    fun `a collision counts up before the extension`() {
        val taken = setOf("notes.txt", "notes (1).txt")
        assertEquals("notes (2).txt", DocumentNames.unique("notes.txt", taken))
    }

    @Test
    fun `an extensionless collision still counts up`() {
        assertEquals("backup (1)", DocumentNames.unique("backup", setOf("backup")))
    }

    @Test
    fun `a dotfile is not treated as all extension`() {
        assertEquals(".bashrc (1)", DocumentNames.unique(".bashrc", setOf(".bashrc")))
    }

    @Test
    fun `rename keeps the extension the user left off`() {
        assertEquals("todo.txt", DocumentNames.renamed("notes.txt", "todo"))
    }

    @Test
    fun `rename honours an extension the user typed`() {
        assertEquals("todo.md", DocumentNames.renamed("notes.txt", "todo.md"))
    }

    @Test
    fun `renaming something with no extension adds none`() {
        assertEquals("archive", DocumentNames.renamed("backup", "archive"))
    }
}
