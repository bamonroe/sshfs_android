package com.bam.sshfs.net.ssh

import com.bam.sshfs.data.model.DEFAULT_SSH_PORT
import com.bam.sshfs.net.ExtraArgs

/** One hop in a `ProxyJump` chain, written `[user@]host[:port]`. */
data class JumpHost(
    val address: String,
    val port: Int = DEFAULT_SSH_PORT,
    /** Login name for this hop, or null to reuse the target host's identity. */
    val username: String? = null,
)

/**
 * The subset of `ssh_config` options the transport actually honours, parsed from a
 * host's freeform extra arguments (see [ExtraArgs]).
 *
 * Options this layer can't act on are collected in [ignored] rather than silently
 * dropped, so the UI can tell the user their line had no effect instead of leaving
 * them to wonder.
 */
data class SshOptions(
    val jumpHosts: List<JumpHost> = emptyList(),
    val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    /** Keepalive interval in seconds; 0 disables them. */
    val keepAliveSeconds: Int = 0,
    val compression: Boolean = false,
    /**
     * True when an unknown host key must be rejected instead of trusted on first
     * use — `StrictHostKeyChecking yes`. Defaults to false (trust on first use).
     */
    val strictHostKeyChecking: Boolean = false,
    val ignored: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000

        /** Parse [extraArgs] — the raw text of `Host.extraArgs`. */
        fun from(extraArgs: String): SshOptions {
            var options = SshOptions()
            val ignored = mutableListOf<String>()
            for ((name, value) in ExtraArgs.parse(extraArgs)) {
                options = options.apply(name, value) ?: run {
                    ignored += name
                    options
                }
            }
            return options.copy(ignored = ignored.distinct())
        }
    }

    /** Fold one option in, or return null when this layer doesn't understand it. */
    private fun apply(name: String, value: String): SshOptions? = when {
        name.equals(ExtraArgs.PROXY_JUMP, true) -> copy(jumpHosts = jumpHosts + parseJumpChain(value))
        name.equals("ConnectTimeout", true) ->
            value.toIntOrNull()?.let { copy(connectTimeoutMillis = it * 1000) }
        name.equals("ServerAliveInterval", true) ->
            value.toIntOrNull()?.let { copy(keepAliveSeconds = it) }
        name.equals("Compression", true) -> copy(compression = value.isYes())
        name.equals("StrictHostKeyChecking", true) ->
            copy(strictHostKeyChecking = value.equals("yes", true))
        else -> null
    }
}

/** `yes`/`true`/`on`, the spellings `ssh_config` and users both accept. */
private fun String.isYes(): Boolean =
    equals("yes", true) || equals("true", true) || equals("on", true)

/** `ProxyJump` takes a comma-separated chain, nearest hop first. */
internal fun parseJumpChain(value: String): List<JumpHost> =
    value.split(',').mapNotNull { hop -> parseJumpHost(hop.trim()) }

/** One `[user@]host[:port]` hop; null when the host part is missing. */
internal fun parseJumpHost(spec: String): JumpHost? {
    if (spec.isEmpty()) return null
    val at = spec.lastIndexOf('@')
    val username = if (at > 0) spec.substring(0, at) else null
    val hostAndPort = spec.substring(at + 1)
    // Only a trailing `:port` splits; a bare IPv6 literal has colons of its own.
    val colon = hostAndPort.lastIndexOf(':')
    val port = if (colon > 0) hostAndPort.substring(colon + 1).toIntOrNull() else null
    val address = if (port != null) hostAndPort.substring(0, colon) else hostAndPort
    if (address.isEmpty()) return null
    return JumpHost(address, port ?: DEFAULT_SSH_PORT, username)
}
