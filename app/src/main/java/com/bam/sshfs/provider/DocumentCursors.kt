package com.bam.sshfs.provider

import android.database.MatrixCursor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.net.ssh.RemoteEntry

/**
 * The SAF column projections and the two row builders the provider fills.
 *
 * Kept apart from [SshfsDocumentsProvider] so the cursor shape — which columns exist
 * and what each remote attribute maps onto — can be read and unit-tested without the
 * provider's threading and session lookup around it.
 */
object DocumentCursors {

    /** Columns returned when the caller asks for no particular projection. */
    val DEFAULT_ROOT_COLUMNS = arrayOf(
        Root.COLUMN_ROOT_ID,
        Root.COLUMN_DOCUMENT_ID,
        Root.COLUMN_TITLE,
        Root.COLUMN_SUMMARY,
        Root.COLUMN_FLAGS,
        Root.COLUMN_ICON,
    )

    val DEFAULT_DOCUMENT_COLUMNS = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
    )

    fun rootCursor(projection: Array<String>?): MatrixCursor =
        MatrixCursor(projection ?: DEFAULT_ROOT_COLUMNS)

    fun documentCursor(projection: Array<String>?): MatrixCursor =
        MatrixCursor(projection ?: DEFAULT_DOCUMENT_COLUMNS)

    /**
     * Add one connected [host] as a picker root, opening at [documentId].
     *
     * `IS_CHILD` keeps the server out of the picker's top-level "recent" shortcuts —
     * every listing costs an SFTP round trip, so Android should only walk it when the
     * user actually opens it. `SUPPORTS_CREATE` lets other apps pick a server as a
     * *save* destination, which is the whole point of a writable root.
     */
    fun addRoot(cursor: MatrixCursor, host: Host, documentId: DocumentId) {
        cursor.newRow()
            .add(Root.COLUMN_ROOT_ID, host.id.toString())
            .add(Root.COLUMN_DOCUMENT_ID, documentId.toString())
            .add(Root.COLUMN_TITLE, host.name)
            .add(Root.COLUMN_SUMMARY, "${host.address}:${host.port}")
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_SUPPORTS_CREATE)
            .add(Root.COLUMN_ICON, android.R.drawable.ic_menu_share)
    }

    /** Add one remote directory entry as a document row. */
    fun addDocument(cursor: MatrixCursor, hostId: Long, entry: RemoteEntry) {
        val mimeType = DocumentMimeTypes.of(entry.name, entry.isDirectory)
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, DocumentId(hostId, entry.path).toString())
            .add(Document.COLUMN_DISPLAY_NAME, entry.name)
            .add(Document.COLUMN_MIME_TYPE, mimeType)
            .add(Document.COLUMN_SIZE, entry.size)
            .add(Document.COLUMN_LAST_MODIFIED, entry.modifiedMillis)
            .add(Document.COLUMN_FLAGS, flagsFor(entry))
    }

    /**
     * The capability flags for one entry.
     *
     * SAF treats every flag as a promise it will show the user a failure for, so each
     * one is derived from the permission bits `RemoteEntry` carries rather than
     * advertised unconditionally. Those bits are only the server's *hint* — it still
     * enforces the truth — but claiming write on a file whose owner bit is clear is
     * the case that reliably fails, and this avoids it.
     *
     * Delete and rename are properties of the *parent* directory in POSIX, not of the
     * entry; the parent isn't in hand here, so the entry's own writability is used as
     * the closest available proxy.
     */
    private fun flagsFor(entry: RemoteEntry): Int {
        var flags = 0
        if (entry.isDirectory) {
            if (entry.writable) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        } else if (entry.writable) {
            flags = flags or Document.FLAG_SUPPORTS_WRITE
        }
        if (entry.writable) {
            flags = flags or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        }
        return flags
    }
}
