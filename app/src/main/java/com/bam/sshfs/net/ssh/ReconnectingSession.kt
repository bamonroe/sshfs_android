package com.bam.sshfs.net.ssh

/**
 * How many times, and how long apart, a dropped link is re-dialled.
 *
 * A pure value so the policy can be unit-tested without a server: mobile networks
 * drop connections constantly and a file picker must not surface every blip.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val backoffMillis: Long = 500,
) {
    /** True when [attempt] (1-based, the one that just failed) may be retried. */
    fun shouldRetry(attempt: Int, error: Throwable): Boolean =
        attempt < maxAttempts && error.looksTransient()

    /** Exponential backoff before the attempt after [attempt]. */
    fun delayAfter(attempt: Int): Long = backoffMillis shl (attempt - 1).coerceAtMost(4)
}

/**
 * An [SftpSession] that re-dials underneath itself when the link drops.
 *
 * Every call runs against the live session; a *transient* failure closes it,
 * reconnects through the [connect] factory, and runs the call again. Anything the
 * server actually answered — a missing file, a rejected password — is passed
 * straight through, because retrying it would only repeat the same answer.
 *
 * Not thread-safe by accident: a `synchronized` block guards the swap, since the
 * `DocumentsProvider` calls in from several handler threads at once.
 */
class ReconnectingSession(
    private val policy: RetryPolicy = RetryPolicy(),
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val connect: () -> SftpSession,
) : SftpSession {

    private val lock = Any()
    private var current: SftpSession? = null
    private var closed = false

    override val isAlive: Boolean get() = synchronized(lock) { current?.isAlive == true }

    override val serverVersion: String get() = session().serverVersion

    override val fingerprint: String get() = session().fingerprint

    override fun list(path: String) = retry { it.list(path) }

    override fun stat(path: String) = retry { it.stat(path) }

    override fun canonicalize(path: String) = retry { it.canonicalize(path) }

    override fun open(path: String, write: Boolean, create: Boolean, truncate: Boolean) =
        retry { it.open(path, write, create, truncate) }

    override fun mkdir(path: String) = retry { it.mkdir(path) }

    override fun rename(from: String, to: String) = retry { it.rename(from, to) }

    override fun delete(path: String, isDirectory: Boolean) = retry { it.delete(path, isDirectory) }

    override fun close() {
        synchronized(lock) {
            closed = true
            current?.let { runCatching { it.close() } }
            current = null
        }
    }

    /** Run [block] against a live session, re-dialling on a transient failure. */
    private fun <T> retry(block: (SftpSession) -> T): T {
        var attempt = 1
        while (true) {
            val live = session()
            try {
                return block(live)
            } catch (e: Exception) {
                if (!policy.shouldRetry(attempt, e)) throw e.asTransportException("Remote call failed")
                discard()
                sleeper(policy.delayAfter(attempt))
                attempt++
            }
        }
    }

    /** The live session, connecting on first use or after a drop. */
    private fun session(): SftpSession = synchronized(lock) {
        check(!closed) { "Session is closed." }
        current?.takeIf { it.isAlive } ?: connect().also {
            current?.let { old -> runCatching { old.close() } }
            current = it
        }
    }

    private fun discard() = synchronized(lock) {
        current?.let { runCatching { it.close() } }
        current = null
    }
}
