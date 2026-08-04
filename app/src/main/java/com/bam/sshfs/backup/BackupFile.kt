package com.bam.sshfs.backup

/** Naming and framing for the file the user saves — the app-free half of export. */
object BackupFile {

    /** What the save dialog is told the document is; the payload is plain text. */
    const val MIME_TYPE = "application/octet-stream"

    /** The extension the app writes and the restore picker expects to see. */
    const val EXTENSION = "sshfsbackup"

    /** A config-only export is plain JSON, and says so, because it is meant to be read. */
    const val CONFIG_MIME_TYPE = "application/json"

    /** The extension for the secret-free variant — a different name so the two don't mix. */
    const val CONFIG_EXTENSION = "sshfsconfig.json"

    /**
     * The name to offer in the save dialog, dated so successive backups sit next to
     * each other in a folder rather than overwriting one another.
     *
     * [today] is `YYYY-MM-DD`; the caller supplies it because this object is meant to
     * stay clock-free and testable.
     */
    fun suggestedFileName(today: String): String = "sshfs-backup-$today.$EXTENSION"

    /** As [suggestedFileName], for the unencrypted config-only export. */
    fun suggestedConfigFileName(today: String): String = "sshfs-config-$today.$CONFIG_EXTENSION"
}
