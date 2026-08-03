package com.bam.sshfs.net.ssh

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectingSessionTest {

    /** A session that fails its first [failures] calls, then answers. */
    private class FakeSession(
        private val label: String,
        private var failures: Int = 0,
        private val error: () -> Exception = { IOException("link dropped") },
    ) : SftpSession {
        var closed = false
        override val isAlive: Boolean get() = !closed
        override val serverVersion = "SSH-2.0-fake"
        override val fingerprint = "SHA256:fake"

        override fun list(path: String): List<RemoteEntry> {
            if (failures-- > 0) throw error()
            return listOf(RemoteEntry("$path/$label", label, false, false, 1, 0, true, true))
        }

        override fun stat(path: String) = throw UnsupportedOperationException()
        override fun canonicalize(path: String) = path
        override fun open(path: String, write: Boolean, create: Boolean, truncate: Boolean) =
            throw UnsupportedOperationException()

        override fun mkdir(path: String) = Unit
        override fun rename(from: String, to: String) = Unit
        override fun delete(path: String, isDirectory: Boolean) = Unit
        override fun close() {
            closed = true
        }
    }

    @Test
    fun `connects lazily, on the first call`() {
        var connects = 0
        val session = ReconnectingSession(sleeper = {}) { connects++; FakeSession("a") }

        assertEquals(0, connects)
        session.list("/")
        session.list("/")
        assertEquals("one connection is reused", 1, connects)
    }

    @Test
    fun `a dropped link is re-dialled and the call retried`() {
        val sessions = mutableListOf<FakeSession>()
        val session = ReconnectingSession(sleeper = {}) {
            FakeSession("s${sessions.size}", failures = if (sessions.isEmpty()) 1 else 0)
                .also { sessions += it }
        }

        assertEquals("s1", session.list("/").single().name)
        assertEquals(2, sessions.size)
        assertTrue("the dead session is closed", sessions[0].closed)
    }

    @Test
    fun `a server-side failure is not retried`() {
        var connects = 0
        val session = ReconnectingSession(sleeper = {}) {
            connects++
            FakeSession("a", failures = 9) {
                SshTransportException(SshFailure.REMOTE, "No such file")
            }
        }

        val thrown = assertThrows(SshTransportException::class.java) { session.list("/missing") }
        assertEquals(SshFailure.REMOTE, thrown.failure)
        assertEquals("no reconnect for an answer the server gave", 1, connects)
    }

    @Test
    fun `retries stop at the policy limit`() {
        var connects = 0
        val session = ReconnectingSession(RetryPolicy(maxAttempts = 3), sleeper = {}) {
            connects++
            FakeSession("a", failures = 9)
        }

        val thrown = assertThrows(SshTransportException::class.java) { session.list("/") }
        assertEquals(SshFailure.NETWORK, thrown.failure)
        assertEquals(3, connects)
    }

    @Test
    fun `closing releases the live session and refuses further calls`() {
        val fake = FakeSession("a")
        val session = ReconnectingSession(sleeper = {}) { fake }
        session.list("/")
        session.close()

        assertTrue(fake.closed)
        assertThrows(IllegalStateException::class.java) { session.list("/") }
    }

    @Test
    fun `backoff grows with each attempt`() {
        val policy = RetryPolicy(backoffMillis = 100)
        assertEquals(100, policy.delayAfter(1))
        assertEquals(200, policy.delayAfter(2))
        assertEquals(400, policy.delayAfter(3))
    }
}
