package com.bam.sshfs.backup

/** Naming and framing for the file the user saves — the app-free half of export. */
object BackupFile {

    /** What the save dialog is told the document is; the payload is plain text. */
    const val MIME_TYPE = "application/octet-stream"

    /** The extension the app writes and the restore picker expects to see. */
    const val EXTENSION = "sshfsbackup"

    /**
     * The name to offer in the save dialog, dated so successive backups sit next to
     * each other in a folder rather than overwriting one another.
     *
     * [today] is `YYYY-MM-DD`; the caller supplies it because this object is meant to
     * stay clock-free and testable.
     */
    fun suggestedFileName(today: String): String = "sshfs-backup-$today.$EXTENSION"
}
