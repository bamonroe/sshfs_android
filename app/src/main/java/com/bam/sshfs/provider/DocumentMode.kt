package com.bam.sshfs.provider

/**
 * A SAF `openDocument` mode string, parsed into the flags SFTP's `open` takes.
 *
 * Parsed here rather than with `ParcelFileDescriptor.parseMode` so it stays a pure
 * value that plain JVM unit tests can cover: the mapping from mode characters to
 * remote open flags is the part that can silently truncate a user's file, and it
 * deserves a test that doesn't need a device.
 *
 * The characters are SAF's own: `r` read, `w` write, `t` truncate, `a` append.
 */
data class DocumentMode(
    val read: Boolean,
    val write: Boolean,
    val truncate: Boolean,
    val append: Boolean,
) {
    /** True when the caller may only look, so no write path needs to be opened. */
    val readOnly: Boolean get() = !write

    companion object {

        /**
         * Parse [mode], rejecting anything SAF doesn't define.
         *
         * A bare `w` **truncates**, matching `ParcelFileDescriptor` and every other
         * provider: an app that opens for write and rewrites from offset zero would
         * otherwise leave the tail of the old, longer file behind.
         */
        fun parse(mode: String): DocumentMode {
            require(mode.isNotEmpty()) { "Empty open mode." }
            var read = false
            var write = false
            var truncate = false
            var append = false
            for (character in mode) {
                when (character) {
                    'r' -> read = true
                    'w' -> write = true
                    't' -> truncate = true
                    'a' -> append = true
                    else -> throw IllegalArgumentException("Unsupported open mode: $mode")
                }
            }
            require(read || write) { "Unsupported open mode: $mode" }
            // `w` alone means "replace the contents"; `wa` explicitly asks to keep them.
            if (write && !read && !append) truncate = true
            return DocumentMode(read = read, write = write, truncate = truncate, append = append)
        }
    }
}
