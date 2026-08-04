package com.bam.sshfs.backup

/**
 * The config-only view of a [BackupDocument]: everything about the setup, none of
 * the secrets.
 *
 * The encrypted backup exists to move an install wholesale, so it has to carry the
 * private keys. This variant exists for the opposite need — sharing a set of hosts
 * with a colleague, or checking the shape of a configuration into version control —
 * where the file must be readable by eye and safe to hand around. So it ships
 * **unencrypted**, and everything that would make that dangerous is stripped here:
 * private key halves, key passphrases, and identity passwords. Public keys stay,
 * because they are public.
 *
 * A key with no private half restores as a *placeholder*: name, type, fingerprint and
 * every link to it survive, and the user supplies the private material afterwards
 * (Keys → Supply private key). That is what makes the links worth keeping — a
 * restored host still points at "work laptop key", it just can't connect until that
 * key is filled in.
 */
object ConfigOnly {

    /** Strip every secret from [document], leaving the configuration around them. */
    fun redact(document: BackupDocument) = document.copy(
        keys = document.keys.map { it.copy(privateKey = "", passphrase = null) },
        identities = document.identities.map { it.copy(password = null) },
    )

    /** True when [key] came out of a config-only file and has no private material. */
    fun isPlaceholder(key: BackupKey): Boolean = key.privateKey.isEmpty()

    /** True when [document] carries no secret material at all. */
    fun isRedacted(document: BackupDocument): Boolean =
        document.keys.all { isPlaceholder(it) && it.passphrase == null } &&
            document.identities.all { it.password == null }
}
