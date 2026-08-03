package com.bam.sshfs.net.ssh

import com.bam.sshfs.data.model.Host
import java.io.Closeable
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.password.PasswordUtils

/**
 * Builds authenticated [SftpSession]s.
 *
 * One place owns the whole connect recipe — options, host-key trust, the jump-host
 * chain, and the order authentication methods are tried in — so the probe, the
 * connection service, and the `DocumentsProvider` can never disagree about what
 * "connect to this host" means.
 */
class SshConnector(private val knownHosts: KnownHostsStore) {

    /**
     * Connect to [host] as [credentials], tunnelling through any `ProxyJump` hops
     * in the host's extra arguments.
     *
     * @throws SshTransportException with the failure classified — see [SshFailure].
     */
    fun connect(host: Host, credentials: SshCredentials): SftpSession {
        require(credentials.hasCredential) { "Identity has neither a password nor a key." }
        val options = SshOptions.from(host.extraArgs)
        val opened = mutableListOf<Closeable>()
        try {
            // Each hop is reached *through* the previous one, so the chain is built
            // nearest-first and the target is the last link.
            var tunnel: SSHClient? = null
            for (jump in options.jumpHosts) {
                val hop = open(jump.address, jump.port, options, credentials, jump.username, tunnel)
                opened += hop.client
                tunnel = hop.client
            }
            val target = open(
                host.address.trim(), host.port, options, credentials, credentials.username, tunnel,
            )
            return SshjSftpSession(
                sftp = target.client.newSFTPClient(),
                client = target.client,
                jumpChain = opened.toList(),
                serverVersion = target.client.transport.serverVersion.orEmpty(),
                fingerprint = target.fingerprint,
            )
        } catch (e: Exception) {
            opened.asReversed().forEach { runCatching { it.close() } }
            throw e.asTransportException("Could not connect to ${host.address}")
        }
    }

    /** One authenticated hop, plus the fingerprint it was verified against. */
    private class Hop(val client: SSHClient, val fingerprint: String)

    private fun open(
        address: String,
        port: Int,
        options: SshOptions,
        credentials: SshCredentials,
        username: String?,
        through: SSHClient?,
    ): Hop {
        val client = SSHClient(configFor(options))
        client.connectTimeout = options.connectTimeoutMillis
        client.timeout = options.connectTimeoutMillis
        if (options.compression) client.useCompression()
        val verifier = TofuHostKeyVerifier(knownHosts, address, port, options.strictHostKeyChecking)
        client.addHostKeyVerifier(verifier)
        try {
            if (through == null) {
                client.connect(address, port)
            } else {
                // A direct-tcpip channel on the previous hop carries this handshake.
                client.connectVia(through.newDirectConnection(address, port))
            }
        } catch (e: Exception) {
            runCatching { client.close() }
            throw verifier.rejection ?: e.asTransportException("Could not reach $address")
        }
        if (options.keepAliveSeconds > 0) {
            client.connection.keepAlive.keepAliveInterval = options.keepAliveSeconds
        }
        authenticate(client, username ?: credentials.username, credentials, address)
        return Hop(client, verifier.presented.orEmpty())
    }

    /** Key first, then password — the order `ssh` itself prefers. */
    private fun authenticate(
        client: SSHClient,
        username: String,
        credentials: SshCredentials,
        address: String,
    ) {
        try {
            val key = credentials.privateKey
            if (!key.isNullOrBlank()) {
                val provider = credentials.passphrase?.takeIf { it.isNotEmpty() }
                    ?.let { client.loadKeys(key, null, PasswordUtils.createOneOff(it.toCharArray())) }
                    ?: client.loadKeys(key, null, null)
                client.authPublickey(username, provider)
            } else {
                client.authPassword(username, credentials.password)
            }
        } catch (first: Exception) {
            // A key that the server won't take still leaves a password to try.
            val password = credentials.password
            if (credentials.privateKey.isNullOrBlank() || password.isNullOrEmpty()) {
                runCatching { client.close() }
                throw first.asTransportException("Could not sign in to $address as $username")
            }
            try {
                client.authPassword(username, password)
            } catch (second: Exception) {
                runCatching { client.close() }
                throw second.asTransportException("Could not sign in to $address as $username")
            }
        }
    }

    /** Android's SSHJ config, with keepalives enabled when the host asked for them. */
    private fun configFor(options: SshOptions) = AndroidConfig().apply {
        if (options.keepAliveSeconds > 0) keepAliveProvider = KeepAliveProvider.KEEP_ALIVE
    }
}
