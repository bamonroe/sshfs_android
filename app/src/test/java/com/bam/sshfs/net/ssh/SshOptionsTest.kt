package com.bam.sshfs.net.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshOptionsTest {

    @Test
    fun `empty text yields the defaults`() {
        val options = SshOptions.from("")
        assertTrue(options.jumpHosts.isEmpty())
        assertEquals(SshOptions.DEFAULT_TIMEOUT_MILLIS, options.connectTimeoutMillis)
        assertFalse(options.strictHostKeyChecking)
    }

    @Test
    fun `parses the options the transport honours`() {
        val options = SshOptions.from(
            """
            # a comment
            ConnectTimeout 5
            ServerAliveInterval 30
            Compression yes
            StrictHostKeyChecking yes
            """.trimIndent()
        )
        assertEquals(5_000, options.connectTimeoutMillis)
        assertEquals(30, options.keepAliveSeconds)
        assertTrue(options.compression)
        assertTrue(options.strictHostKeyChecking)
    }

    @Test
    fun `unsupported options are reported rather than dropped silently`() {
        val options = SshOptions.from("SendEnv LANG\nConnectTimeout 5")
        assertEquals(listOf("SendEnv"), options.ignored)
    }

    @Test
    fun `parses a multi-hop proxy jump chain`() {
        val options = SshOptions.from("ProxyJump alice@bastion:2222,gateway")
        assertEquals(
            listOf(JumpHost("bastion", 2222, "alice"), JumpHost("gateway", 22, null)),
            options.jumpHosts,
        )
    }

    @Test
    fun `a jump host without a port or user takes the defaults`() {
        assertEquals(JumpHost("host.example", 22, null), parseJumpHost("host.example"))
    }

    @Test
    fun `a malformed jump host is skipped`() {
        assertEquals(emptyList<JumpHost>(), parseJumpChain("user@ , "))
    }

    @Test
    fun `strict host key checking off is the accept-new default`() {
        assertFalse(SshOptions.from("StrictHostKeyChecking accept-new").strictHostKeyChecking)
    }
}
