package com.bam.sshfs.net.ssh

import java.io.IOException
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.userauth.UserAuthException

/**
 * Classifies an SSHJ failure so callers can tell "try again" from "ask the user".
 *
 * The distinction is load-bearing: [ReconnectingSession] retries only
 * [SshFailure.NETWORK], and retrying a rejected password would lock an account out.
 */
fun Throwable.asTransportException(context: String): SshTransportException {
    if (this is SshTransportException) return this
    val failure = when {
        this is UserAuthException -> SshFailure.AUTHENTICATION
        this is SFTPException && statusCode != Response.StatusCode.UNKNOWN -> SshFailure.REMOTE
        else -> SshFailure.NETWORK
    }
    return SshTransportException(failure, "$context: ${describe(this)}", this)
}

/** True when a dropped link, rather than the server, is the likely cause. */
fun Throwable.looksTransient(): Boolean =
    (this as? SshTransportException)?.transient ?: (this is IOException && this !is SFTPException)

/** The most specific message in the chain — SSHJ wraps its causes heavily. */
private fun describe(error: Throwable): String {
    var cause: Throwable = error
    while (cause.cause != null && cause.message.isNullOrBlank()) cause = cause.cause!!
    return cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
}
