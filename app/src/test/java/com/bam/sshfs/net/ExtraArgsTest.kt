package com.bam.sshfs.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraArgsTest {

    @Test
    fun `blank lines and comments are ignored`() {
        val parsed = ExtraArgs.parse("\n# a note\n  \nPort 2222\n")
        assertEquals(listOf(ExtraArgs.Option("Port", "2222")), parsed)
    }

    @Test
    fun `the value is everything after the keyword`() {
        val parsed = ExtraArgs.parse("ProxyCommand ssh -W %h:%p bastion")
        assertEquals(listOf(ExtraArgs.Option("ProxyCommand", "ssh -W %h:%p bastion")), parsed)
    }

    @Test
    fun `equals and tabs separate a keyword from its value`() {
        assertEquals(ExtraArgs.Option("Port", "22"), ExtraArgs.parse("Port=22").single())
        assertEquals(ExtraArgs.Option("Port", "22"), ExtraArgs.parse("Port\t22").single())
        assertEquals(ExtraArgs.Option("Port", "22"), ExtraArgs.parse("Port = 22").single())
    }

    @Test
    fun `a keyword with no value is reported with its line number`() {
        val problems = ExtraArgs.problems("Port 22\n\nProxyJump\n")
        assertEquals(1, problems.size)
        assertEquals(3, problems.single().line)
        assertEquals(ExtraArgs.Reason.MISSING_VALUE, problems.single().reason)
    }

    @Test
    fun `ProxyJump is detected regardless of case`() {
        assertTrue(ExtraArgs.usesProxyJump("proxyjump bastion"))
        assertFalse(ExtraArgs.usesProxyJump("Port 22"))
    }
}
