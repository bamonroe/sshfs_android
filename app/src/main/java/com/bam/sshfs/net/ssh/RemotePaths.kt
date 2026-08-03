package com.bam.sshfs.net.ssh

/**
 * POSIX path arithmetic for remote paths.
 *
 * `java.io.File` can't be used here: on Android it would work by accident, but the
 * separator and the notion of a root belong to the *server*, not to the device.
 */
object RemotePaths {

    /** Join [parent] and [child], collapsing the separator between them. */
    fun join(parent: String, child: String): String {
        val base = parent.trimEnd('/')
        val name = child.trimStart('/')
        return if (base.isEmpty()) "/$name" else "$base/$name"
    }

    /** The last segment, or the path itself when it has no separator. */
    fun name(path: String): String = path.trimEnd('/').substringAfterLast('/')

    /** The containing directory, or null for the root. */
    fun parent(path: String): String? {
        val trimmed = path.trimEnd('/')
        if (trimmed.isEmpty() || trimmed == "/") return null
        val cut = trimmed.lastIndexOf('/')
        return when {
            cut < 0 -> null
            cut == 0 -> "/"
            else -> trimmed.substring(0, cut)
        }
    }
}
