package com.bam.sshfs.data.repo

/**
 * Thrown when a row can't be deleted because other rows still point at it.
 * The UI turns this into "still used by N identities — unlink them first".
 */
class ReferencedException(val referenceCount: Int, message: String) : IllegalStateException(message)
