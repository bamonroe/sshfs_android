package com.bam.sshfs.net.ssh

/** Why a connection or a remote operation failed, in terms the UI can act on. */
enum class SshFailure {
    /** The address didn't answer, or the link dropped. Retrying may help. */
    NETWORK,

    /** The server rejected the credentials. Retrying will not help. */
    AUTHENTICATION,

    /** First contact with this address and strict checking is on. */
    HOST_KEY_UNKNOWN,

    /** The server's key differs from the remembered one — never retried. */
    HOST_KEY_CHANGED,

    /** The SFTP subsystem said no: missing path, permission denied, full disk. */
    REMOTE,
}

/** A transport-layer failure carrying the [failure] kind alongside the message. */
class SshTransportException(
    val failure: SshFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** True when a fresh connection stands a chance — see [ReconnectingSession]. */
    val transient: Boolean get() = failure == SshFailure.NETWORK
}
