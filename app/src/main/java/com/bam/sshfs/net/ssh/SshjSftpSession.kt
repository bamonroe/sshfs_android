package com.bam.sshfs.net.ssh

import java.io.Closeable
import java.io.IOException
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.xfer.FilePermission

/**
 * The SSHJ-backed [SftpSession].
 *
 * Owns the whole chain it was built from: the SFTP client, the target's
 * [SSHClient], and any jump-host clients behind it. Closing walks the chain in
 * reverse so a jump host is never torn down before the connection tunnelled
 * through it.
 */
class SshjSftpSession(
    private val sftp: SFTPClient,
    private val client: SSHClient,
    /** Jump-host clients, nearest hop first; closed after [client]. */
    private val jumpChain: List<Closeable> = emptyList(),
    override val serverVersion: String,
    override val fingerprint: String,
) : SftpSession {

    override val isAlive: Boolean get() = client.isConnected && client.isAuthenticated

    override fun list(path: String): List<RemoteEntry> = wrap("list $path") {
        sftp.ls(path).map { entry -> entry.attributes.toEntry(entry.path, entry.name) }
    }

    override fun stat(path: String): RemoteEntry = wrap("stat $path") {
        sftp.stat(path).toEntry(path, path.substringAfterLast('/', path))
    }

    override fun canonicalize(path: String): String = wrap("resolve $path") {
        sftp.canonicalize(path)
    }

    override fun open(path: String, write: Boolean, create: Boolean, truncate: Boolean): RemoteHandle {
        val modes = mutableSetOf(OpenMode.READ)
        if (write) modes += OpenMode.WRITE
        if (create) modes += OpenMode.CREAT
        if (truncate) modes += OpenMode.TRUNC
        return wrap("open $path") { SshjRemoteHandle(sftp.open(path, modes)) }
    }

    override fun mkdir(path: String) = wrap("create $path") { sftp.mkdir(path) }

    override fun rename(from: String, to: String) = wrap("rename $from") { sftp.rename(from, to) }

    override fun delete(path: String, isDirectory: Boolean) = wrap("delete $path") {
        if (isDirectory) sftp.rmdir(path) else sftp.rm(path)
    }

    override fun close() {
        // Best effort, innermost first: a half-torn-down chain must not leak sockets.
        listOf(sftp, client).plus(jumpChain.asReversed()).forEach { closeable ->
            runCatching { closeable.close() }
        }
    }
}

/** One open remote file. SSHJ's [RemoteFile] is already offset-addressed. */
private class SshjRemoteHandle(private val file: RemoteFile) : RemoteHandle {

    override fun read(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int =
        wrap("read") { file.read(offset, buffer, bufferOffset, length) }

    override fun write(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int) =
        wrap("write") { file.write(offset, buffer, bufferOffset, length) }

    override fun size(): Long = wrap("size") { file.length() }

    override fun setSize(size: Long) = wrap("truncate") { file.setLength(size) }

    // SSHJ writes synchronously per request, so there is nothing buffered locally;
    // the method exists so the proxy file descriptor callback has one to call.
    override fun flush() = Unit

    override fun close() {
        runCatching { file.close() }
    }
}

/** SFTP attributes → the transport-neutral [RemoteEntry]. */
private fun FileAttributes.toEntry(path: String, name: String): RemoteEntry {
    val permissions = permissions
    return RemoteEntry(
        path = path,
        name = name,
        isDirectory = type == FileMode.Type.DIRECTORY,
        isSymlink = type == FileMode.Type.SYMLINK,
        size = size,
        // SFTP reports whole seconds; SAF wants millis.
        modifiedMillis = mtime * 1000L,
        // The owner bits are the best guess available without trying the operation:
        // the server enforces the truth, and SAF only needs a hint for its flags.
        readable = FilePermission.USR_R in permissions,
        writable = FilePermission.USR_W in permissions,
    )
}

/** Run [block], turning SSHJ's exceptions into [SshTransportException]. */
private inline fun <T> wrap(what: String, block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw e.asTransportException("Could not $what")
    }
