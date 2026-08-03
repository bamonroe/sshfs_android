package com.bam.sshfs.provider

import android.provider.DocumentsContract
import android.webkit.MimeTypeMap

/**
 * MIME types for remote entries.
 *
 * SFTP tells us nothing about content type, so the extension is all we have. The
 * picker filters on these, so a wrong-but-plausible guess is worse than the generic
 * fallback — we only claim a type when Android's own map knows the extension.
 */
object DocumentMimeTypes {

    const val GENERIC = "application/octet-stream"

    /** The MIME type for a remote entry [name], or the directory type when [isDirectory]. */
    fun of(name: String, isDirectory: Boolean): String {
        if (isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return GENERIC
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: GENERIC
    }
}
