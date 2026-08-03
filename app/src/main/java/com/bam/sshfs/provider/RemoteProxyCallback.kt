package com.bam.sshfs.provider

import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import com.bam.sshfs.net.ssh.RemoteHandle

/**
 * Serves one open remote file to the kernel, read and written on demand.
 *
 * This is the piece that makes the app SSHFS-like rather than a downloader: the
 * caller gets a file descriptor immediately and every `read`/`write` on it becomes an
 * offset-addressed SFTP request for just that range, so opening a 4 GB file off a
 * phone costs nothing until something actually reads it.
 *
 * Android runs these callbacks on the handler thread the descriptor was opened with,
 * never on a binder thread — but that thread is still not the host's SFTP thread, so
 * every call is handed to [RemoteWorkers] to keep the channel single-threaded.
 *
 * Failures must leave as [ErrnoException]: the kernel turns the errno into the
 * calling app's `IOException`, whereas an unchecked exception here would take down
 * the whole process.
 */
class RemoteProxyCallback(
    private val hostId: Long,
    private val handle: RemoteHandle,
    private val writable: Boolean,
    /** Called once the descriptor is closed, to tear the handler thread down. */
    private val onClosed: () -> Unit,
) : ProxyFileDescriptorCallback() {

    override fun onGetSize(): Long = guard { handle.size() }

    /**
     * Fill up to [size] bytes at [offset], returning how many were actually read.
     *
     * Loops because a single SFTP read may come back short of the request for
     * reasons that aren't end-of-file — the server's own maximum packet size, most
     * often — and a short return here would look like a truncated file to the caller.
     */
    override fun onRead(offset: Long, size: Int, data: ByteArray): Int = guard {
        var filled = 0
        while (filled < size) {
            val read = handle.read(offset + filled, data, filled, size - filled)
            if (read <= 0) break
            filled += read
        }
        filled
    }

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int = guard {
        if (!writable) throw ErrnoException("onWrite", OsConstants.EBADF)
        handle.write(offset, data, 0, size)
        size
    }

    override fun onFsync() = guard { handle.flush() }

    override fun onRelease() {
        // Never throws: this runs on the close path, and a failure to release would
        // leak the handler thread as well as the remote handle.
        runCatching { RemoteWorkers.call(hostId) { handle.close() } }
        onClosed()
    }

    /**
     * Run [block] on the host's SFTP worker, mapping any failure to an errno.
     *
     * [ErrnoException]s thrown by the block itself pass through unchanged, so
     * [onWrite]'s `EBADF` is not flattened into a generic I/O error.
     */
    private fun <T> guard(block: () -> T): T = try {
        RemoteWorkers.call(hostId, block)
    } catch (e: ErrnoException) {
        throw e
    } catch (e: Exception) {
        throw ErrnoException(e.message ?: e.javaClass.simpleName, OsConstants.EIO, e)
    }
}
