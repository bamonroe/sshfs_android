package com.bam.sshfs.provider

/**
 * Picks the display name a newly created document actually gets.
 *
 * SAF makes this the *provider's* job: `createDocument` is told the name the user
 * asked for and must return a document that exists, so a collision has to be resolved
 * here rather than reported. Android's own providers append ` (1)`, ` (2)`, … and
 * pickers are written expecting that, so this matches it.
 *
 * Pure string work with the sibling names passed in, so it is unit-testable without a
 * server — and so the caller pays for exactly one directory listing.
 */
object DocumentNames {

    /** How many suffixes to try before giving up and letting the server object. */
    private const val ATTEMPTS = 32

    /** [wanted], or the first ` (n)` variant of it that isn't in [taken]. */
    fun unique(wanted: String, taken: Set<String>): String {
        if (wanted !in taken) return wanted
        val extension = extensionOf(wanted)
        val stem = wanted.dropLast(extension.length)
        for (n in 1..ATTEMPTS) {
            val candidate = "$stem ($n)$extension"
            if (candidate !in taken) return candidate
        }
        return wanted
    }

    /**
     * The name a rename should produce, given the [current] name and what the user
     * typed.
     *
     * A rename that only changes the stem keeps the original extension when the user
     * left it off — retyping `notes` for `notes.txt` should not silently strip the
     * type the picker filters on.
     */
    fun renamed(current: String, wanted: String): String {
        val extension = extensionOf(current)
        if (extension.isEmpty()) return wanted
        if (wanted.contains('.')) return wanted
        return "$wanted$extension"
    }

    /**
     * The trailing `.ext` of [name] including the dot, or empty when it has none.
     *
     * A **leading** dot is not an extension: `.bashrc` is a whole name on a Unix
     * server, and splitting it would produce ` (1).bashrc` on a collision and hide the
     * file's real identity.
     */
    private fun extensionOf(name: String): String {
        val cut = name.lastIndexOf('.')
        if (cut <= 0 || cut == name.length - 1) return ""
        return name.substring(cut)
    }
}
