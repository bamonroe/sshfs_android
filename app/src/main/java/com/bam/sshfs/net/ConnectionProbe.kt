package com.bam.sshfs.net

import com.bam.sshfs.data.model.Host
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey

/** What a "Test connection" attempt found. */
sealed interface ProbeResult {
    /**
     * The server answered the SSH handshake. No credentials were offered — this
     * checks that the address, port and network path work, not that the host's
     * identity can sign in.
     */
    data class Reachable(
        val serverVersion: String,
        val fingerprint: String,
        /** True when the host sets [ExtraArgs.PROXY_JUMP], which this probe ignores. */
        val skippedProxyJump: Boolean,
    ) : ProbeResult

    /** The attempt failed; [reason] is the underlying message, already unwrapped. */
    data class Failed(val reason: String) : ProbeResult
}

/**
 * Opens an SSH transport to a host and hangs up, so the editor can tell the user
 * whether the address they typed answers before they rely on it.
 *
 * Deliberately stops short of authentication: credentials are decrypted by the
 * connection manager, not the UI, and a reachability answer is what the user
 * needs while typing an address.
 */
class ConnectionProbe(private val timeoutMillis: Int = 10_000) {

    suspend fun probe(host: Host): ProbeResult = withContext(Dispatchers.IO) {
        try {
            connect(host)
        } catch (e: Exception) {
            ProbeResult.Failed(describe(e))
        }
    }

    /** Handshake with the server, capturing its version banner and host key. */
    private fun connect(host: Host): ProbeResult {
        var hostKey: PublicKey? = null
        val client = SSHClient(AndroidConfig())
        client.connectTimeout = timeoutMillis
        client.timeout = timeoutMillis
        client.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String?, port: Int, key: PublicKey): Boolean {
                hostKey = key
                return true
            }

            override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = emptyList()
        })
        client.use {
            it.connect(host.address.trim(), host.port)
            return ProbeResult.Reachable(
                serverVersion = it.transport.serverVersion.orEmpty(),
                fingerprint = hostKey?.let(SecurityUtils::getFingerprint).orEmpty(),
                skippedProxyJump = ExtraArgs.usesProxyJump(host.extraArgs),
            )
        }
    }

    /** The most specific message in the failure chain, since SSHJ wraps heavily. */
    private fun describe(error: Throwable): String {
        var cause: Throwable = error
        while (cause.cause != null && cause.message.isNullOrBlank()) cause = cause.cause!!
        return cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
    }
}
