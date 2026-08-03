package com.bam.sshfs.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Installs the full Bouncy Castle provider ahead of the cut-down "BC" that Android
 * ships, which is missing algorithms SSHJ needs (Ed25519, bcrypt-KDF OpenSSH keys).
 *
 * Idempotent and safe to call from anywhere that is about to touch key material.
 */
object SshSecurity {
    private const val NAME = BouncyCastleProvider.PROVIDER_NAME

    @Volatile private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        val current = Security.getProvider(NAME)
        if (current == null || current.javaClass != BouncyCastleProvider::class.java) {
            Security.removeProvider(NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
        installed = true
    }
}
