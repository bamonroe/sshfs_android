package com.bam.sshfs.provider

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Runs SFTP work off the binder thread, one worker per host.
 *
 * Two rules the provider depends on:
 *
 * * **Never touch SSHJ from a binder thread.** A stalled TCP read there burns one of
 *   the process's few binder threads and can wedge every SAF client at once, so the
 *   call is handed to a worker and waited on with a timeout instead.
 * * **One worker per host, single-threaded.** An SFTP channel is not safe for
 *   concurrent use; serializing per host also keeps a slow server from starving the
 *   others, which a shared pool would not.
 */
object RemoteWorkers {

    /** How long a SAF call waits before giving up on the server. */
    private const val TIMEOUT_SECONDS = 30L

    private val workers = ConcurrentHashMap<Long, ExecutorService>()

    /**
     * Run [block] on [hostId]'s worker and return its result.
     *
     * Rethrows whatever [block] threw, so the provider's own error mapping still
     * sees the original transport exception; a hung server surfaces as
     * [TimeoutException].
     */
    fun <T> call(hostId: Long, block: () -> T): T {
        val worker = workers.computeIfAbsent(hostId) {
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "sftp-saf-$it").apply { isDaemon = true }
            }
        }
        val future = worker.submit(block)
        return try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw e
        }
    }

    /** Drop [hostId]'s worker; called when its session goes away. */
    fun release(hostId: Long) {
        workers.remove(hostId)?.shutdownNow()
    }
}
