package com.bam.sshfs.net.ssh

import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** [SshjSftpSession] against a live SFTP server — see [EmbeddedSftpServer]. */
class SshjSftpSessionTest {

    private lateinit var root: Path
    private lateinit var server: EmbeddedSftpServer
    private lateinit var session: SftpSession

    @Before
    fun startServer() {
        root = Files.createTempDirectory("sshfs-test")
        server = EmbeddedSftpServer(root)
        session = server.connect()
    }

    @After
    fun stopServer() {
        runCatching { session.close() }
        server.close()
        root.toFile().deleteRecursively()
    }

    @Test
    fun listsFilesAndDirectoriesWithTheirAttributes() {
        Files.write(root.resolve("hello.txt"), "hello".toByteArray())
        Files.createDirectory(root.resolve("sub"))

        val entries = session.list("/").associateBy { it.name }

        assertEquals(5L, entries.getValue("hello.txt").size)
        assertFalse(entries.getValue("hello.txt").isDirectory)
        assertEquals("/hello.txt", entries.getValue("hello.txt").path)
        assertTrue(entries.getValue("sub").isDirectory)
    }

    @Test
    fun statsOnePath() {
        Files.write(root.resolve("hello.txt"), "hello".toByteArray())

        val entry = session.stat("/hello.txt")

        assertEquals("hello.txt", entry.name)
        assertEquals(5L, entry.size)
        assertTrue(entry.readable)
        // SFTP reports seconds; the wrapper's job is to hand back milliseconds.
        assertTrue(entry.modifiedMillis > 1_000_000_000_000L)
    }

    @Test
    fun statOfAMissingPathFails() {
        assertThrows(SshTransportException::class.java) { session.stat("/nope") }
    }

    @Test
    fun canonicalizesTheLoginDirectory() {
        assertEquals("/", session.canonicalize("."))
    }

    @Test
    fun readsAtAnOffset() {
        Files.write(root.resolve("data.bin"), "0123456789".toByteArray())

        session.open("/data.bin").use { handle ->
            assertEquals(10L, handle.size())
            val buffer = ByteArray(4)
            assertEquals(4, handle.read(3, buffer, 0, 4))
            assertArrayEquals("3456".toByteArray(), buffer)
            assertEquals(-1, handle.read(10, buffer, 0, 4))
        }
    }

    @Test
    fun createsAndWritesAFile() {
        session.open("/new.txt", write = true, create = true, truncate = true).use { handle ->
            handle.write(0, "abcdef".toByteArray(), 0, 6)
            handle.flush()
        }

        assertEquals("abcdef", String(Files.readAllBytes(root.resolve("new.txt"))))
    }

    @Test
    fun truncatesThroughSetSize() {
        Files.write(root.resolve("trim.txt"), "0123456789".toByteArray())

        session.open("/trim.txt", write = true).use { it.setSize(4) }

        assertEquals("0123", String(Files.readAllBytes(root.resolve("trim.txt"))))
    }

    @Test
    fun makesRenamesAndDeletes() {
        session.mkdir("/dir")
        assertTrue(Files.isDirectory(root.resolve("dir")))

        session.rename("/dir", "/moved")
        assertTrue(Files.isDirectory(root.resolve("moved")))

        session.delete("/moved", isDirectory = true)
        assertFalse(Files.exists(root.resolve("moved")))
    }

    @Test
    fun reportsItsOwnLiveness() {
        assertTrue(session.isAlive)
        assertTrue(session.serverVersion.isNotEmpty())
        session.close()
        assertFalse(session.isAlive)
    }
}
