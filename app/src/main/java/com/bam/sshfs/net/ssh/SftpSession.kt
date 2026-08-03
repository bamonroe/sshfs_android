package com.bam.sshfs.net.ssh

import java.io.Closeable

/**
 * One remote directory entry or file, in the shape SAF needs.
 *
 * Deliberately a plain value type with no SSHJ types in it: the
 * `DocumentsProvider` and the metadata cache work in these, so the SSH library
 * stops at this package's edge and can be swapped without touching them.
 */
data class RemoteEntry(
    /** Absolute remote path, the document id the provider hands out. */
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    /** Last-modified time in **milliseconds** since the epoch (SFTP reports seconds). */
    val modifiedMillis: Long,
    val readable: Boolean,
    val writable: Boolean,
)

/** An open remote file, read and written at arbitrary offsets. */
interface RemoteHandle : Closeable {
    /** Read up to [length] bytes at [offset]; returns -1 at end of file. */
    fun read(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int
    fun write(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int)
    fun size(): Long
    /** Truncate or extend the file — what SAF's "write from the start" needs. */
    fun setSize(size: Long)
    fun flush()
}

/**
 * The transport as the rest of the app sees it: an authenticated SFTP session
 * exposing exactly the operations the `DocumentsProvider` needs.
 *
 * Every method blocks and throws [SshTransportException]; callers run them off the
 * binder thread. [SshjSftpSession] is the real implementation, [ReconnectingSession]
 * the wrapper that survives a dropped link.
 */
interface SftpSession : Closeable {

    /** True while the underlying transport is up. */
    val isAlive: Boolean

    /** The server's version banner, for the connections screen. */
    val serverVersion: String

    /** The host-key fingerprint this session authenticated against. */
    val fingerprint: String

    fun list(path: String): List<RemoteEntry>

    fun stat(path: String): RemoteEntry

    /** Resolve `.`, `~` and relative paths to an absolute one. */
    fun canonicalize(path: String): String

    /**
     * Open [path] for reading, or for reading and writing when [write] is set;
     * [create] adds the file if it is missing, [truncate] empties an existing one.
     */
    fun open(
        path: String,
        write: Boolean = false,
        create: Boolean = false,
        truncate: Boolean = false,
    ): RemoteHandle

    fun mkdir(path: String)

    fun rename(from: String, to: String)

    /** Remove a file, or an empty directory. */
    fun delete(path: String, isDirectory: Boolean = false)
}
