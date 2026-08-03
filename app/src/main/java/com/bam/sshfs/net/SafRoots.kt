package com.bam.sshfs.net

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * The SAF authority this app publishes roots under, and the one call that tells
 * Android the set of roots changed.
 *
 * Lives in `net/` rather than the (not yet written) provider package because the
 * connection manager is what actually *changes* the root set: a host coming up or
 * going down is the only reason the picker's list is stale. The authority string
 * must match the `android:authorities` on the provider in the manifest.
 */
object SafRoots {

    const val AUTHORITY = "com.bam.sshfs.documents"

    /** `content://com.bam.sshfs.documents/root` — what the file picker observes. */
    val uri: Uri = DocumentsContract.buildRootsUri(AUTHORITY)

    /**
     * Ask every SAF client to re-query [uri].
     *
     * Safe before the provider exists: with nothing registered the notification
     * simply reaches no observers.
     */
    fun notifyChanged(context: Context) {
        context.contentResolver.notifyChange(uri, null)
    }
}
