package com.bam.sshfs.provider

import com.bam.sshfs.net.ssh.RemoteEntry
import com.bam.sshfs.net.ssh.RemotePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Time is injected, so these assert on the TTL boundary without sleeping. */
class MetadataCacheTest {

    private var clock = 0L
    private val cache = MetadataCache(ttlMillis = 1_000L, now = { clock })

    private fun entry(path: String, size: Long = 1L) = RemoteEntry(
        path = path,
        name = RemotePaths.name(path),
        isDirectory = false,
        isSymlink = false,
        size = size,
        modifiedMillis = 0L,
        readable = true,
        writable = true,
    )

    @Test
    fun `second listing inside the ttl does not hit the server`() {
        var loads = 0
        repeat(3) {
            cache.listing(1L, "/home") { loads++; listOf(entry("/home/a")) }
        }
        assertEquals(1, loads)
    }

    @Test
    fun `listing reloads once the ttl has passed`() {
        var loads = 0
        cache.listing(1L, "/home") { loads++; emptyList() }
        clock += 1_000L
        cache.listing(1L, "/home") { loads++; emptyList() }
        assertEquals(2, loads)
    }

    @Test
    fun `hosts do not share cache entries under the same path`() {
        cache.listing(1L, "/home") { listOf(entry("/home/a")) }
        val other = cache.listing(2L, "/home") { listOf(entry("/home/b")) }
        assertEquals(listOf("b"), other.map { it.name })
    }

    @Test
    fun `a listing seeds the stat of every child it saw`() {
        cache.listing(1L, "/home") { listOf(entry("/home/a", size = 42L)) }
        val stat = cache.stat(1L, "/home/a") { error("should not stat after a listing") }
        assertEquals(42L, stat.size)
    }

    @Test
    fun `invalidating a directory drops its listing and its children's stats`() {
        cache.listing(1L, "/home") { listOf(entry("/home/a")) }
        cache.invalidateDirectory(1L, "/home")

        var loads = 0
        cache.listing(1L, "/home") { loads++; emptyList() }
        cache.stat(1L, "/home/a") { loads++; entry("/home/a") }
        assertEquals(2, loads)
    }

    @Test
    fun `invalidating a directory leaves other directories alone`() {
        cache.listing(1L, "/home") { listOf(entry("/home/a")) }
        cache.listing(1L, "/etc") { listOf(entry("/etc/b")) }
        cache.invalidateDirectory(1L, "/home")

        val kept = cache.listing(1L, "/etc") { error("unrelated listing was evicted") }
        assertEquals(listOf("b"), kept.map { it.name })
    }

    @Test
    fun `invalidating a path drops its stat and its parent's listing`() {
        cache.listing(1L, "/home") { listOf(entry("/home/a")) }
        cache.invalidatePath(1L, "/home/a")

        var loads = 0
        cache.stat(1L, "/home/a") { loads++; entry("/home/a") }
        cache.listing(1L, "/home") { loads++; emptyList() }
        assertEquals(2, loads)
    }

    @Test
    fun `invalidating a host clears it and only it`() {
        cache.listing(1L, "/home") { listOf(entry("/home/a")) }
        cache.listing(2L, "/home") { listOf(entry("/home/b")) }
        cache.invalidateHost(1L)

        var reloaded = false
        cache.listing(1L, "/home") { reloaded = true; emptyList() }
        val kept = cache.listing(2L, "/home") { error("other host was evicted") }
        assertTrue(reloaded)
        assertEquals(listOf("b"), kept.map { it.name })
    }
}
