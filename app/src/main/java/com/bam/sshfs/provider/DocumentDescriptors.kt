package com.bam.sshfs.provider

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import com.bam.sshfs.net.ssh.RemoteHandle
import java.io.File
import java.io.FileNotFoundException

/**
 * Turns an open [RemoteHandle] into the file descriptor SAF hands the calling app.
 *
 * Two strategies, in order of preference:
 *
 * 1. **[StorageManager.openProxyFileDescriptor]** — the streaming path, where each
 *    read and write on the descriptor becomes an SFTP request for that byte range.
 *    This is what the whole design wants, but it needs the platform's FUSE-backed
 *    proxy, which some devices and emulator images simply don't provide.
 * 2. **A cached temp file** — download once, hand back a plain descriptor, upload on
 *    close. Correct everywhere, and much worse for large files, so it is only used
 *    when the proxy refuses.
 *
 * The fallback is chosen by *catching* the proxy's failure rather than by checking an
 * API level: `openProxyFileDescriptor` exists since API 26 but throws
 * `UnsupportedOperationException` at runtime where the backing implementation is
 * missing, and there is no way to ask in advance.
 */
object DocumentDescriptors {

    /** Cache subdirectory holding fallback copies; cleared as each one closes. */
    private const val CACHE_DIR = "saf-cache"

    /**
     * Open [handle] as a descriptor for a document named [name] in [mode].
     *
     * Takes ownership of [handle]: it is closed when the descriptor is, on whichever
     * path is taken, and closed here if the descriptor can't be produced at all.
     */
    fun open(
        context: Context,
        hostId: Long,
        name: String,
        mode: DocumentMode,
        rawMode: String,
        handle: RemoteHandle,
    ): ParcelFileDescriptor = try {
        streaming(context, hostId, mode, handle)
    } catch (e: UnsupportedOperationException) {
        cached(context, hostId, name, rawMode, mode, handle)
    } catch (e: Exception) {
        runCatching { handle.close() }
        throw e
    }

    /** The streaming path: one handler thread per open file, released with it. */
    private fun streaming(
        context: Context,
        hostId: Long,
        mode: DocumentMode,
        handle: RemoteHandle,
    ): ParcelFileDescriptor {
        val storage = context.getSystemService(StorageManager::class.java)
            ?: throw UnsupportedOperationException("No StorageManager available.")
        // A thread per open file, not a shared one: callbacks block on the network,
        // and one app streaming a large file must not stall another app's reads.
        val thread = HandlerThread("sftp-fd-$hostId").apply { start() }
        val callback = RemoteProxyCallback(hostId, handle, mode.write) { thread.quitSafely() }
        return try {
            storage.openProxyFileDescriptor(
                if (mode.write) ParcelFileDescriptor.MODE_READ_WRITE
                else ParcelFileDescriptor.MODE_READ_ONLY,
                callback,
                Handler(thread.looper),
            )
        } catch (e: Exception) {
            thread.quitSafely()
            throw e
        }
    }

    /**
     * The fallback path: a temp copy, uploaded again when the caller closes it.
     *
     * The upload runs on the close listener's handler thread and pushes the bytes
     * through the same [handle], so it still lands on the host's SFTP worker.
     */
    private fun cached(
        context: Context,
        hostId: Long,
        name: String,
        rawMode: String,
        mode: DocumentMode,
        handle: RemoteHandle,
    ): ParcelFileDescriptor {
        val file = tempFile(context, hostId, name)
        val thread = HandlerThread("sftp-cache-$hostId").apply { start() }
        try {
            if (!mode.truncate) download(hostId, handle, file)
            return ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.parseMode(rawMode),
                Handler(thread.looper),
            ) {
                if (mode.write) runCatching { upload(hostId, handle, file) }
                runCatching { RemoteWorkers.call(hostId) { handle.close() } }
                file.delete()
                thread.quitSafely()
            }
        } catch (e: Exception) {
            thread.quitSafely()
            file.delete()
            runCatching { handle.close() }
            throw e
        }
    }

    private fun download(hostId: Long, handle: RemoteHandle, file: File) {
        RemoteWorkers.call(hostId) {
            val buffer = ByteArray(BUFFER_BYTES)
            file.outputStream().use { out ->
                var offset = 0L
                while (true) {
                    val read = handle.read(offset, buffer, 0, buffer.size)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    offset += read
                }
            }
        }
    }

    private fun upload(hostId: Long, handle: RemoteHandle, file: File) {
        RemoteWorkers.call(hostId) {
            val buffer = ByteArray(BUFFER_BYTES)
            file.inputStream().use { input ->
                var offset = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    handle.write(offset, buffer, 0, read)
                    offset += read
                }
                // The local copy is the truth now, so a shortened file must shrink
                // remotely too — otherwise the old tail survives past the new end.
                handle.setSize(offset)
            }
            handle.flush()
        }
    }

    private fun tempFile(context: Context, hostId: Long, name: String): File {
        val directory = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val suffix = name.substringAfterLast('.', "").take(EXTENSION_LIMIT)
        return try {
            // The prefix must be at least three characters, hence the literal.
            File.createTempFile("sftp-$hostId-", if (suffix.isEmpty()) null else ".$suffix", directory)
        } catch (e: java.io.IOException) {
            throw FileNotFoundException("Could not cache $name: ${e.message}")
        }
    }

    private const val BUFFER_BYTES = 64 * 1024
    private const val EXTENSION_LIMIT = 16
}
