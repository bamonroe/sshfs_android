package com.bam.sshfs.ui.hosts

import com.bam.sshfs.data.model.DEFAULT_SSH_PORT
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.net.ExtraArgs

/** Why a draft host can't be saved yet. */
enum class HostFormError { BLANK_NAME, BLANK_ADDRESS, BAD_PORT, BAD_EXTRA_ARGS }

/**
 * The editable state of one host.
 *
 * [port] stays a string while the user types — an empty field means "the default
 * SSH port" rather than an error, which a numeric field can't express.
 */
data class HostForm(
    val id: Long = 0,
    val name: String = "",
    val address: String = "",
    val port: String = "",
    val defaultIdentityId: Long? = null,
    val remoteRoot: String = "",
    val extraArgs: String = "",
    val createdAt: Long = 0,
) {
    /** The port to save: the typed number, or the default when left blank. */
    val effectivePort: Int get() = port.trim().toIntOrNull() ?: DEFAULT_SSH_PORT

    /** The first problem blocking a save, or null when the draft is valid. */
    fun validate(): HostFormError? = when {
        name.isBlank() -> HostFormError.BLANK_NAME
        address.isBlank() -> HostFormError.BLANK_ADDRESS
        !portIsValid() -> HostFormError.BAD_PORT
        ExtraArgs.problems(extraArgs).isNotEmpty() -> HostFormError.BAD_EXTRA_ARGS
        else -> null
    }

    /** A blank port is the default; anything typed must be a real TCP port. */
    private fun portIsValid(): Boolean {
        val typed = port.trim()
        if (typed.isEmpty()) return true
        return typed.toIntOrNull() in 1..65535
    }

    /** The stored row this draft describes; [now] stamps a newly created host. */
    fun toHost(now: Long): Host = Host(
        id = id,
        name = name.trim(),
        address = address.trim(),
        port = effectivePort,
        defaultIdentityId = defaultIdentityId,
        remoteRoot = remoteRoot.trim().ifEmpty { "." },
        extraArgs = extraArgs.trim(),
        createdAt = if (createdAt == 0L) now else createdAt,
    )

    companion object {
        /** Start a draft from a stored host. */
        fun of(host: Host) = HostForm(
            id = host.id,
            name = host.name,
            address = host.address,
            port = host.port.toString(),
            defaultIdentityId = host.defaultIdentityId,
            remoteRoot = host.remoteRoot,
            extraArgs = host.extraArgs,
            createdAt = host.createdAt,
        )
    }
}
