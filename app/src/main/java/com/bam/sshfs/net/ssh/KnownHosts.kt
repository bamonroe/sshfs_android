package com.bam.sshfs.net.ssh

import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import net.schmizz.sshj.common.Buffer

/** What the app remembers about one server's host key. */
data class KnownHost(
    val address: String,
    val port: Int,
    /** `SHA256:…`, the same spelling OpenSSH prints. */
    val fingerprint: String,
)

/**
 * The app's trust store for server host keys.
 *
 * Trust-on-first-use: an address seen for the first time is remembered, and every
 * later connection must present the same key. A *changed* key is never accepted
 * silently — that is the one failure mode host-key checking exists to catch.
 */
interface KnownHostsStore {
    fun lookup(address: String, port: Int): KnownHost?
    fun remember(host: KnownHost)
    fun forget(address: String, port: Int)
    fun all(): List<KnownHost>
}

/** In-memory store; the unit tests' stand-in, and a sane default before setup. */
class InMemoryKnownHostsStore(initial: List<KnownHost> = emptyList()) : KnownHostsStore {
    private val entries = LinkedHashMap<String, KnownHost>()

    init {
        initial.forEach { remember(it) }
    }

    override fun lookup(address: String, port: Int) = entries[key(address, port)]
    override fun remember(host: KnownHost) {
        entries[key(host.address, host.port)] = host
    }

    override fun forget(address: String, port: Int) {
        entries.remove(key(address, port))
    }

    override fun all(): List<KnownHost> = entries.values.toList()

    private fun key(address: String, port: Int) = "${address.lowercase()}:$port"
}

/**
 * A [KnownHostsStore] persisted as one `host:port SHA256:…` line per entry.
 *
 * A plain text file rather than a Room table: it holds no secrets, it is the
 * artefact a user might want to inspect or delete by hand, and the transport must
 * be able to read it without the database being open.
 */
class FileKnownHostsStore(private val file: File) : KnownHostsStore {

    private val cache by lazy { InMemoryKnownHostsStore(read()) }

    override fun lookup(address: String, port: Int) = cache.lookup(address, port)

    override fun remember(host: KnownHost) {
        cache.remember(host)
        write()
    }

    override fun forget(address: String, port: Int) {
        cache.forget(address, port)
        write()
    }

    override fun all(): List<KnownHost> = cache.all()

    private fun read(): List<KnownHost> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { parseLine(it.trim()) }
    }

    private fun write() {
        file.parentFile?.mkdirs()
        file.writeText(cache.all().joinToString("\n") { "${it.address}:${it.port} ${it.fingerprint}" } + "\n")
    }

    /** `host:port fingerprint`; blank lines, comments and junk are skipped. */
    private fun parseLine(line: String): KnownHost? {
        if (line.isEmpty() || line.startsWith("#")) return null
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2) return null
        val colon = parts[0].lastIndexOf(':')
        if (colon <= 0) return null
        val port = parts[0].substring(colon + 1).toIntOrNull() ?: return null
        return KnownHost(parts[0].substring(0, colon), port, parts[1])
    }
}

/** OpenSSH's `SHA256:` fingerprint of a host key, base64 without padding. */
fun fingerprintOf(key: PublicKey): String {
    val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
    val digest = MessageDigest.getInstance("SHA-256").digest(blob)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
}
