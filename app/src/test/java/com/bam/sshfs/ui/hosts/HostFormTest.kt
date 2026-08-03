package com.bam.sshfs.ui.hosts

import com.bam.sshfs.data.model.DEFAULT_SSH_PORT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostFormTest {

    private fun valid() = HostForm(name = "Box", address = "example.com")

    @Test
    fun `a named host with an address is valid`() {
        assertNull(valid().validate())
    }

    @Test
    fun `name and address are required`() {
        assertEquals(HostFormError.BLANK_NAME, valid().copy(name = " ").validate())
        assertEquals(HostFormError.BLANK_ADDRESS, valid().copy(address = "").validate())
    }

    @Test
    fun `a blank port means the default rather than an error`() {
        assertNull(valid().copy(port = "").validate())
        assertEquals(DEFAULT_SSH_PORT, valid().copy(port = "").effectivePort)
    }

    @Test
    fun `a port outside the TCP range is rejected`() {
        assertEquals(HostFormError.BAD_PORT, valid().copy(port = "0").validate())
        assertEquals(HostFormError.BAD_PORT, valid().copy(port = "65536").validate())
        assertNull(valid().copy(port = "2222").validate())
    }

    @Test
    fun `an extra argument without a value blocks the save`() {
        assertEquals(HostFormError.BAD_EXTRA_ARGS, valid().copy(extraArgs = "ProxyJump").validate())
        assertNull(valid().copy(extraArgs = "ProxyJump bastion.example.com").validate())
    }

    @Test
    fun `saving trims the draft and defaults the remote root`() {
        val host = valid().copy(address = " example.com ", remoteRoot = "  ").toHost(now = 5)
        assertEquals("example.com", host.address)
        assertEquals(".", host.remoteRoot)
        assertEquals(5L, host.createdAt)
    }

    @Test
    fun `editing an existing host keeps its creation stamp`() {
        val host = valid().copy(id = 7, createdAt = 100).toHost(now = 999)
        assertEquals(100L, host.createdAt)
    }
}
