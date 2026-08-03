package com.bam.sshfs.crypto

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The process-wide gate holder — who can prompt, and what happens when nobody can. */
class SecretAuthGateTest {

    private class FakeGate(private val answer: Boolean) : AuthGate {
        var calls = 0
        override suspend fun authenticate(title: String, subtitle: String): Boolean {
            calls++
            return answer
        }
    }

    @After
    fun clear() {
        // The holder is a singleton; leaving a gate registered would leak into other tests.
        SecretAuthGate.register(NO_GATE)
        SecretAuthGate.unregister(NO_GATE)
    }

    @Test
    fun `reports failure when no activity is registered`() = runBlocking {
        assertFalse(SecretAuthGate.available)
        assertFalse(SecretAuthGate.authenticate("t", "s"))
    }

    @Test
    fun `delegates to the registered gate`() = runBlocking {
        val gate = FakeGate(answer = true)
        SecretAuthGate.register(gate)
        assertTrue(SecretAuthGate.available)
        assertTrue(SecretAuthGate.authenticate("t", "s"))
        assertEquals(1, gate.calls)
    }

    @Test
    fun `a declined prompt is reported as failure`() = runBlocking {
        SecretAuthGate.register(FakeGate(answer = false))
        assertFalse(SecretAuthGate.authenticate("t", "s"))
    }

    @Test
    fun `unregistering a stale gate leaves the current one alone`() = runBlocking {
        val stale = FakeGate(answer = false)
        val current = FakeGate(answer = true)
        SecretAuthGate.register(stale)
        SecretAuthGate.register(current)
        // The new activity starts before the old one stops; its unregister must not win.
        SecretAuthGate.unregister(stale)
        assertTrue(SecretAuthGate.authenticate("t", "s"))
        assertEquals(1, current.calls)
    }

    private companion object {
        val NO_GATE = object : AuthGate {
            override suspend fun authenticate(title: String, subtitle: String) = false
        }
    }
}
