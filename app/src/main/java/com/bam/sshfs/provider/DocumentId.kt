package com.bam.sshfs.provider

import com.bam.sshfs.net.ssh.RemotePaths

/**
 * A SAF document id: which connected host, and which absolute path on it.
 *
 * SAF hands document ids back to us as opaque strings, so they have to carry the
 * host themselves — the picker may query a document long after the user last
 * touched that root, with no other context. The encoding is `hostId:/abs/path`;
 * the first `:` splits it, so a remote path containing a colon survives intact.
 */
data class DocumentId(val hostId: Long, val path: String) {

    /** The wire form handed to SAF. */
    override fun toString(): String = "$hostId$SEPARATOR$path"

    /** The id of a child of this document named [name]. */
    fun child(name: String): DocumentId = DocumentId(hostId, RemotePaths.join(path, name))

    /**
     * True when [other] lies somewhere beneath this document on the same host.
     *
     * Pure path arithmetic, deliberately: SAF asks this while drawing breadcrumbs
     * and while checking persisted grants, far too often to spend a round trip on.
     */
    fun contains(other: DocumentId): Boolean =
        hostId == other.hostId && other.path.startsWith(path.trimEnd('/') + "/")

    /** The id of the containing directory, or null when this is the root. */
    fun parent(): DocumentId? = RemotePaths.parent(path)?.let { DocumentId(hostId, it) }

    companion object {
        private const val SEPARATOR = ':'

        /** The root document id for a host, once its remote root is canonicalized. */
        fun root(hostId: Long, path: String): DocumentId = DocumentId(hostId, path)

        /**
         * Parse a document id SAF gave us back.
         *
         * Throws [IllegalArgumentException] on anything malformed: a bad id is a
         * caller bug or a stale persisted grant, and the provider turns it into a
         * `FileNotFoundException` rather than guessing at a path.
         */
        fun parse(documentId: String): DocumentId {
            val cut = documentId.indexOf(SEPARATOR)
            require(cut > 0) { "Malformed document id: $documentId" }
            val hostId = documentId.substring(0, cut).toLongOrNull()
                ?: throw IllegalArgumentException("Malformed document id: $documentId")
            val path = documentId.substring(cut + 1)
            require(path.startsWith("/")) { "Document id is not an absolute path: $documentId" }
            return DocumentId(hostId, path)
        }
    }
}
