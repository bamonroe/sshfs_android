package com.bam.sshfs.net.ssh

import java.security.PublicKey
import net.schmizz.sshj.transport.verification.HostKeyVerifier

/**
 * Trust-on-first-use host-key verification against a [KnownHostsStore].
 *
 * - **Known and matching** → accepted.
 * - **Unknown** → remembered and accepted, unless [strict] (`StrictHostKeyChecking
 *   yes`), in which case it is refused.
 * - **Known but different** → always refused. This is the man-in-the-middle case,
 *   and clearing it is a deliberate user action ([KnownHostsStore.forget]), never
 *   something the transport does on its own.
 *
 * The rejection reason is kept in [rejection] because SSHJ turns a `false` return
 * into a generic transport error; the connector reads it to raise a precise
 * [SshTransportException].
 */
class TofuHostKeyVerifier(
    private val store: KnownHostsStore,
    private val address: String,
    private val port: Int,
    private val strict: Boolean = false,
) : HostKeyVerifier {

    /** The fingerprint the server presented, once the handshake has run. */
    var presented: String? = null
        private set

    /** Set when [verify] refused the key, with the kind and the expected value. */
    var rejection: SshTransportException? = null
        private set

    override fun verify(hostname: String?, port: Int, key: PublicKey): Boolean {
        val fingerprint = fingerprintOf(key)
        presented = fingerprint
        val known = store.lookup(address, this.port)
        return when {
            known == null && strict -> reject(
                SshFailure.HOST_KEY_UNKNOWN,
                "The host key for $address is not known yet ($fingerprint) and strict " +
                    "checking is on. Connect once without StrictHostKeyChecking to trust it.",
            )

            known == null -> {
                store.remember(KnownHost(address, this.port, fingerprint))
                true
            }

            known.fingerprint == fingerprint -> true

            else -> reject(
                SshFailure.HOST_KEY_CHANGED,
                "The host key for $address changed: expected ${known.fingerprint}, " +
                    "got $fingerprint. Forget the old key only if you know why it changed.",
            )
        }
    }

    override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = emptyList()

    private fun reject(failure: SshFailure, message: String): Boolean {
        rejection = SshTransportException(failure, message)
        return false
    }
}
