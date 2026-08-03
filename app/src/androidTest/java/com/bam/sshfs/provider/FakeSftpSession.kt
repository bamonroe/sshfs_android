package com.bam.sshfs.provider

import com.bam.sshfs.net.ssh.RemoteEntry
import com.bam.sshfs.net.ssh.RemoteHandle
import com.bam.sshfs.net.ssh.RemotePaths
import com.bam.sshfs.net.ssh.SftpSession
import com.bam.sshfs.net.ssh.SshFailure
import com.bam.sshfs.net.ssh.SshTransportException

/**
 * An in-memory remote filesystem behind the [SftpSession] seam.
 *
 * The SAF tests are about the provider — document ids, cursors, descriptors, and
 * the notifications SAF depends on — so the transport is replaced wholesale here.
 * [SshjSftpSessionTest][com.bam.sshfs.net.ssh.SshjSftpSessionTest] covers the real
 * one against a live server; running both against one fixture would test neither
 * well.
 */
class FakeSftpSession : SftpSession {

    /** Absolute path to contents; a directory is a null value. */
    private val files = linkedMapOf<String, ByteArray?>("/" to null)

    override val isAlive = true
    override val serverVersion = "SSH-2.0-Fake"
    override val fingerprint = "SHA256:fake"

    fun putFile(path: String, contents: String) {
        files[path] = contents.toByteArray()
    }

    fun putDirectory(path: String) {
        files[path] = null
    }

    fun contentsOf(path: String): String? = files[path]?.let { String(it) }

    fun exists(path: String): Boolean = files.containsKey(path)

    override fun list(path: String): List<RemoteEntry> {
        if (!entryOf(path).isDirectory) fail("Not a directory: $path")
        val prefix = path.trimEnd('/') + "/"
        return files.keys
            .filter { it != path && it.startsWith(prefix) && !it.removePrefix(prefix).contains('/') }
            .map { entryOf(it) }
    }

    override fun stat(path: String): RemoteEntry = entryOf(path)

    override fun canonicalize(path: String): String = if (path == ".") "/" else path

    override fun open(
        path: String,
        write: Boolean,
        create: Boolean,
        truncate: Boolean,
    ): RemoteHandle {
        if (!files.containsKey(path)) {
            if (!create) fail("No such file: $path")
            files[path] = ByteArray(0)
        } else if (truncate) {
            files[path] = ByteArray(0)
        }
        return Handle(path)
    }

    override fun mkdir(path: String) {
        if (files.containsKey(path)) fail("Exists: $path")
        files[path] = null
    }

    override fun rename(from: String, to: String) {
        if (!files.containsKey(from)) fail("No such file: $from")
        files[to] = files.remove(from)
    }

    override fun delete(path: String, isDirectory: Boolean) {
        if (!files.containsKey(path)) fail("No such file: $path")
        files.remove(path)
    }

    override fun close() = Unit

    /** What the real transport does with any SFTP-level refusal. */
    private fun fail(message: String): Nothing =
        throw SshTransportException(SshFailure.REMOTE, message)

    private fun entryOf(path: String): RemoteEntry {
        if (!files.containsKey(path)) fail("No such file: $path")
        val body = files[path]
        return RemoteEntry(
            path = path,
            name = if (path == "/") "/" else RemotePaths.name(path),
            isDirectory = body == null,
            isSymlink = false,
            size = body?.size?.toLong() ?: 0L,
            modifiedMillis = 1_700_000_000_000L,
            readable = true,
            writable = true,
        )
    }

    private inner class Handle(private val path: String) : RemoteHandle {

        private fun body() = files[path] ?: fail("Gone: $path")

        override fun read(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
            val body = body()
            if (offset >= body.size) return -1
            val count = minOf(length.toLong(), body.size - offset).toInt()
            System.arraycopy(body, offset.toInt(), buffer, bufferOffset, count)
            return count
        }

        override fun write(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int) {
            val body = body()
            val grown = if (offset + length > body.size) {
                body.copyOf((offset + length).toInt())
            } else {
                body
            }
            System.arraycopy(buffer, bufferOffset, grown, offset.toInt(), length)
            files[path] = grown
        }

        override fun size(): Long = body().size.toLong()

        override fun setSize(size: Long) {
            files[path] = body().copyOf(size.toInt())
        }

        override fun flush() = Unit

        override fun close() = Unit
    }
}
