package com.bam.sshfs.provider

import com.bam.sshfs.net.ssh.RemoteEntry
import com.bam.sshfs.net.ssh.RemotePaths
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-TTL memory of directory listings and `stat` results, keyed by host and path.
 *
 * File pickers query aggressively — a single directory view will `queryChildDocuments`
 * once and then `queryDocument` every row, often re-running the lot on every redraw —
 * while an SFTP round trip costs a network hop each time. Caching for a few seconds
 * turns that storm into one listing.
 *
 * The TTL is deliberately *short* rather than clever: entries expire on their own, so
 * a file changed by someone else on the server shows up almost immediately, and the
 * cache never has to be right about anything for long. Writes we make ourselves don't
 * wait for the TTL — the provider invalidates the affected directory, and a dropped
 * session invalidates the whole host.
 *
 * A listing also fills the stat entries for everything it saw: the picker's follow-up
 * `queryDocument` calls are exactly the rows the listing just returned.
 *
 * Safe for concurrent use — SAF calls arrive on many binder threads at once.
 */
class MetadataCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    /** Injectable so tests can move time without sleeping. */
    private val now: () -> Long = System::currentTimeMillis,
) {

    private data class Key(val hostId: Long, val path: String)

    private class Stamped<T>(val value: T, val storedAt: Long)

    private val listings = ConcurrentHashMap<Key, Stamped<List<RemoteEntry>>>()
    private val stats = ConcurrentHashMap<Key, Stamped<RemoteEntry>>()

    /**
     * Return the cached listing of [path], or run [load] and cache what it produces.
     *
     * Also seeds the stat of every child, since the listing already carries it.
     */
    fun listing(hostId: Long, path: String, load: () -> List<RemoteEntry>): List<RemoteEntry> {
        fresh(listings, Key(hostId, path))?.let { return it }
        val entries = load()
        listings[Key(hostId, path)] = Stamped(entries, now())
        for (entry in entries) stats[Key(hostId, entry.path)] = Stamped(entry, now())
        return entries
    }

    /** Return the cached `stat` of [path], or run [load] and cache what it produces. */
    fun stat(hostId: Long, path: String, load: () -> RemoteEntry): RemoteEntry {
        fresh(stats, Key(hostId, path))?.let { return it }
        val entry = load()
        stats[Key(hostId, path)] = Stamped(entry, now())
        return entry
    }

    /**
     * Forget [path]'s listing and the stats of everything directly inside it.
     *
     * Called after a create/delete/rename: the directory's contents changed, and so did
     * the metadata of the child we touched. The child's own subtree is left alone —
     * those entries are stale only if the child moved, and a rename invalidates both
     * ends anyway.
     */
    fun invalidateDirectory(hostId: Long, path: String) {
        listings.remove(Key(hostId, path))
        stats.keys.removeAll { it.hostId == hostId && RemotePaths.parent(it.path) == path }
    }

    /** Forget one path's `stat`, plus the listing of the directory holding it. */
    fun invalidatePath(hostId: Long, path: String) {
        stats.remove(Key(hostId, path))
        RemotePaths.parent(path)?.let { listings.remove(Key(hostId, it)) }
    }

    /**
     * Forget everything about [hostId].
     *
     * Called when the session goes away. A later reconnect gets a genuinely new view of
     * the server — it may even land on a different machine behind a load balancer — so
     * nothing from the old one is worth keeping, and a host may be re-added with the
     * same id pointing somewhere else entirely.
     */
    fun invalidateHost(hostId: Long) {
        listings.keys.removeAll { it.hostId == hostId }
        stats.keys.removeAll { it.hostId == hostId }
    }

    private fun <T> fresh(map: ConcurrentHashMap<Key, Stamped<T>>, key: Key): T? {
        val stamped = map[key] ?: return null
        if (now() - stamped.storedAt >= ttlMillis) {
            map.remove(key, stamped)
            return null
        }
        return stamped.value
    }

    companion object {
        /**
         * Long enough to collapse a picker's burst of queries, short enough that an
         * external change is never hidden for more than a moment.
         */
        const val DEFAULT_TTL_MILLIS = 5_000L

        /** The instance the provider uses; process-wide, like the sessions it mirrors. */
        val shared = MetadataCache()
    }
}
