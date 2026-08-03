package com.bam.sshfs.net

/**
 * The freeform "extra connect arguments" a host carries: `ssh_config`-style
 * options, one `Option value` per line, blank lines and `#` comments allowed.
 *
 * ```
 * ProxyJump bastion.example.com
 * ServerAliveInterval 30
 * ```
 *
 * Parsing lives here rather than in the UI because the transport layer reads the
 * same text when it opens a connection.
 */
object ExtraArgs {

    /** One parsed option: the keyword as written, and everything after it. */
    data class Option(val name: String, val value: String)

    /** An option keyword whose value names a jump host, handled by the transport. */
    const val PROXY_JUMP = "ProxyJump"

    /** Why a line of extra arguments can't be parsed. */
    data class Problem(val line: Int, val text: String, val reason: Reason)

    enum class Reason { MISSING_VALUE }

    /**
     * Parse [text] into options, ignoring blanks and comments. Lines that are
     * malformed are skipped here and reported by [problems].
     */
    fun parse(text: String): List<Option> =
        significantLines(text).mapNotNull { (_, line) -> option(line) }

    /** Every malformed line in [text], for the editor to show inline. */
    fun problems(text: String): List<Problem> =
        significantLines(text).mapNotNull { (number, line) ->
            if (option(line) == null) Problem(number, line, Reason.MISSING_VALUE) else null
        }

    /** True when [text] sets an option the direct-connection probe can't honour. */
    fun usesProxyJump(text: String): Boolean =
        parse(text).any { it.name.equals(PROXY_JUMP, ignoreCase = true) }

    /** The 1-based, trimmed lines that aren't blank or comments. */
    private fun significantLines(text: String): List<Pair<Int, String>> =
        text.lines()
            .mapIndexed { index, line -> index + 1 to line.trim() }
            .filter { (_, line) -> line.isNotEmpty() && !line.startsWith("#") }

    /** Split one line into keyword and value; null when the value is missing. */
    private fun option(line: String): Option? {
        val separator = line.indexOfFirst { it == ' ' || it == '\t' || it == '=' }
        if (separator <= 0) return null
        val value = line.substring(separator + 1).trim().trimStart('=').trim()
        return if (value.isEmpty()) null else Option(line.substring(0, separator), value)
    }
}
