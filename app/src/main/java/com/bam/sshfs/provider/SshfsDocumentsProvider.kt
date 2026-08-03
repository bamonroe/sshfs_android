package com.bam.sshfs.provider

import android.database.Cursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import com.bam.sshfs.net.ConnectionManager
import com.bam.sshfs.net.SafRoots
import com.bam.sshfs.net.ssh.RemoteEntry
import com.bam.sshfs.net.ssh.RemotePaths
import java.io.FileNotFoundException

/**
 * Publishes each connected host as a root in the system file picker.
 *
 * This is the SSHFS analogue for Android: with no FUSE available to an unprivileged
 * app, SAF is the only way remote files reach other apps, and this class is the whole
 * seam. It owns *no* state — sessions live in [ConnectionManager], so a provider
 * instance created by a cold binder call sees exactly the same connections the UI
 * does.
 *
 * Every remote call goes through [RemoteWorkers] rather than running inline: SAF
 * calls arrive on binder threads, and a stalled SFTP read on one of those can wedge
 * unrelated clients.
 */
class SshfsDocumentsProvider : DocumentsProvider() {

    private val connections: ConnectionManager
        get() = ConnectionManager.get(appContext())

    override fun onCreate(): Boolean = true

    /**
     * One row per connected host. Disconnected hosts are omitted rather than shown
     * as unavailable: a root the picker cannot open is worse than no root, and
     * [SafRoots.notifyChanged] re-queries this the moment a connection comes up.
     */
    override fun queryRoots(projection: Array<String>?): Cursor {
        val cursor = DocumentCursors.rootCursor(projection)
        for (connected in connections.connected()) {
            DocumentCursors.addRoot(
                cursor,
                connected.host,
                DocumentId.root(connected.host.id, connected.rootPath),
            )
        }
        cursor.setNotificationUri(appContext().contentResolver, SafRoots.uri)
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val id = parse(documentId)
        val cursor = DocumentCursors.documentCursor(projection)
        DocumentCursors.addDocument(cursor, id.hostId, statOf(id))
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val id = parse(parentDocumentId)
        val session = sessionOf(id)
        val entries = remote(id) { session.list(id.path) }
        val cursor = DocumentCursors.documentCursor(projection)
        for (entry in entries) DocumentCursors.addDocument(cursor, id.hostId, entry)
        cursor.setNotificationUri(
            appContext().contentResolver,
            DocumentsContract.buildChildDocumentsUri(SafRoots.AUTHORITY, parentDocumentId),
        )
        return cursor
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = runCatching { parse(parentDocumentId) }.getOrNull() ?: return false
        val child = runCatching { parse(documentId) }.getOrNull() ?: return false
        return parent.contains(child)
    }

    override fun getDocumentType(documentId: String): String {
        val id = parse(documentId)
        return DocumentMimeTypes.of(RemotePaths.name(id.path), statOf(id).isDirectory)
    }

    /** `stat` one path, mapped into the value type the cursor builder takes. */
    private fun statOf(id: DocumentId): RemoteEntry {
        val session = sessionOf(id)
        return remote(id) { session.stat(id.path) }
    }

    private fun sessionOf(id: DocumentId) = connections.sessionOf(id.hostId)
        ?: throw FileNotFoundException("Host ${id.hostId} is not connected.")

    /**
     * Run remote [block] on the host's worker, mapping any failure to the
     * [FileNotFoundException] SAF's contract allows — the picker shows an
     * "unavailable" item instead of crashing the calling app.
     */
    private fun <T> remote(id: DocumentId, block: () -> T): T = try {
        RemoteWorkers.call(id.hostId, block)
    } catch (e: Exception) {
        throw FileNotFoundException("${id.path}: ${e.message ?: e.javaClass.simpleName}")
    }

    /** `ContentProvider.requireContext` is API 30; this project ships back to 26. */
    private fun appContext() = context ?: error("DocumentsProvider used before onCreate.")

    private fun parse(documentId: String): DocumentId = try {
        DocumentId.parse(documentId)
    } catch (e: IllegalArgumentException) {
        throw FileNotFoundException(e.message)
    }

    /**
     * Not implemented yet — streaming file I/O is its own piece of work, and no
     * document row advertises a read or write flag until it lands.
     */
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor =
        throw FileNotFoundException("Opening remote files is not implemented yet.")
}
