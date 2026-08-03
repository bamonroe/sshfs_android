package com.bam.sshfs.net.ssh

import java.io.Closeable
import java.nio.file.Path
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory

/**
 * A real SFTP server, in this JVM, serving [root].
 *
 * The point is that [SshjSftpSession] is exercised over the wire — a mocked
 * `SFTPClient` would prove nothing about attribute mapping, open modes, or how
 * SSHJ reports a missing file, which is exactly what the wrapper exists to
 * translate. Binds an ephemeral port and accepts one fixed password.
 */
class EmbeddedSftpServer(root: Path) : Closeable {

    private val server: SshServer = SshServer.setUpDefaultServer().apply {
        host = "127.0.0.1"
        port = 0
        keyPairProvider = SimpleGeneratorHostKeyProvider(root.resolve("hostkey.ser"))
        setPasswordAuthenticator { user, password, _ -> user == USER && password == PASSWORD }
        subsystemFactories = listOf(SftpSubsystemFactory())
        fileSystemFactory = VirtualFileSystemFactory(root)
        start()
    }

    /** The port the server actually bound, known only after [start]. */
    val port: Int get() = server.port

    /** An authenticated session against this server, ready to use in a test. */
    fun connect(): SftpSession {
        val client = SSHClient().apply {
            // The host key is generated per test run, so trust-on-first-use has
            // nothing to pin against; verification is not what these tests cover.
            addHostKeyVerifier(PromiscuousVerifier())
            connect("127.0.0.1", port)
            authPassword(USER, PASSWORD)
        }
        return SshjSftpSession(
            sftp = client.newSFTPClient(),
            client = client,
            serverVersion = client.transport.serverVersion,
            fingerprint = "test",
        )
    }

    override fun close() {
        server.stop(true)
    }

    companion object {
        const val USER = "tester"
        const val PASSWORD = "hunter2"
    }
}
