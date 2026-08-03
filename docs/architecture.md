# Architecture

How **sshfs_android** works internally: the data model, the repository layout, and the
design decisions behind them. `CLAUDE.md` is the hub and points here; `README.md` owns
how a user builds and runs the app.

## Repository layout

| Path | What lives there |
|------|------------------|
| `app/src/main/java/com/bam/sshfs/` | The app module root — `MainActivity` and packages below |
| `…/data/model/` | Room entities: `SshKey`, `Identity`, `Host` |
| `…/data/db/` | `SshfsDatabase`, the DAOs, and the enum `Converters` |
| `…/data/repo/` | Repositories: CRUD plus the referential-integrity rules |
| `…/crypto/` | Key generation, import/parsing, OpenSSH text formats, secret storage |
| `…/ui/` | Compose screens and the Material 3 theme |
| `…/ui/keys/` | The Keys screen: list, generate, import, show/copy public key |
| `…/ui/identities/` | The Identities screen: list, editor form + validation, delete/unlink |
| `…/ui/hosts/` | The Hosts screen: list, editor form + validation, connection test |
| `…/ui/connections/` | The Connections screen: per-host state and the connect/disconnect controls |
| `…/ui/shell/` | The single-activity shell: the `Destination` enum and its bottom nav bar |
| `…/net/` | Transport-facing helpers: `ExtraArgs`, `ConnectionProbe`, `ConnectionRegistry` |
| `…/net/ssh/` | The SSH/SFTP transport: connect, host-key trust, remote file operations |
| `app/schemas/` | Room's exported schema JSON (generated; also an androidTest asset) |
| `docs/` | The spoke docs — this file and `tools.md` |

## Data model

Three **separate first-class entities**, persisted with Room in a single database
(`sshfs.db`, `SshfsDatabase`). Keeping keys, identities, and hosts apart means one key can
back many identities and one identity can serve many hosts, without duplicating secrets.

```
SshKey  ◀──(keyId, nullable)──  Identity  ◀──(defaultIdentityId, nullable)──  Host
```

### `SshKey` — table `ssh_keys`

| Column | Type | Notes |
|--------|------|-------|
| `id` | Long | auto-generated primary key |
| `name` | String | user-facing label |
| `type` | `KeyType` | `ED25519` · `RSA` · `ECDSA`, stored by name |
| `privateKeyCiphertext` | String | Keystore-encrypted private key, base64 |
| `publicKey` | String | OpenSSH-format public key, plaintext |
| `hasPassphrase` | Boolean | whether the private key is passphrase-protected |
| `passphraseCiphertext` | String? | Keystore-encrypted passphrase, else null |
| `origin` | `KeyOrigin` | `GENERATED` on-device or `IMPORTED` |
| `createdAt` | Long | epoch millis |

### `Identity` — table `identities`

| Column | Type | Notes |
|--------|------|-------|
| `id` | Long | auto-generated primary key |
| `name` | String | user-facing label |
| `username` | String | the remote login name |
| `passwordCiphertext` | String? | Keystore-encrypted password, else null |
| `keyId` | Long? | FK → `ssh_keys.id`, `RESTRICT` on delete, indexed |
| `createdAt` | Long | epoch millis |

An identity may carry a password, a key, or both; one with neither cannot connect.

### `Host` — table `hosts`

| Column | Type | Notes |
|--------|------|-------|
| `id` | Long | auto-generated primary key |
| `name` | String | user-facing label; also the SAF root title |
| `address` | String | hostname or IP |
| `port` | Int | defaults to `DEFAULT_SSH_PORT` (22) |
| `defaultIdentityId` | Long? | FK → `identities.id`, `RESTRICT` on delete, indexed |
| `remoteRoot` | String | directory the SAF root opens at; `.` = login directory |
| `extraArgs` | String | freeform ssh options (proxy/jump, ciphers…), one per line |
| `createdAt` | Long | epoch millis |

### Referential integrity — blocked, or unlinked explicitly

Both foreign keys are `ON DELETE RESTRICT`, and `SshfsDatabase` turns on
`PRAGMA foreign_keys` in its open callback, so SQLite refuses an orphaning delete rather
than silently succeeding. The repositories surface that as a decision instead of a crash:

- `KeyRepository.delete(key)` throws `ReferencedException` when identities still reference
  the key; `delete(key, unlink = true)` clears those `keyId` links first, then deletes.
- `IdentityRepository.delete(identity)` behaves the same way against hosts'
  `defaultIdentityId`.
- `HostRepository.delete(host)` is unconditional — nothing references a host.

The UI is expected to catch `ReferencedException`, show the reference count, and re-call
with `unlink = true` if the user confirms.

### Why Room, and why secrets are ciphertext columns

Room gives compile-time-checked SQL, `Flow` observation for Compose, and a versioned
schema exported to `app/schemas/` for migrations. Secrets never sit in plaintext: private
keys, passphrases, and passwords are stored as Keystore-encrypted blobs, so the database
file alone is not enough to authenticate anywhere.

## Key material — generation, import, and the text forms

`…/crypto/` owns everything that turns bytes into a key and back. It is deliberately free
of Android APIs (no `android.util.Base64`, no `Context`) so the whole layer is covered by
plain JVM unit tests in `app/src/test/`.

| Type | Job |
|------|-----|
| `KeyPairFactory` | Generates Ed25519 and RSA-3072 pairs on-device with Bouncy Castle |
| `KeyImporter` | Parses a pasted or picked private key, and reports whether it is passphrase-protected |
| `OpenSshFormat` | The `ssh-ed25519 AAAA… comment` line, its `SHA256:` fingerprint, and PEM wrapping |
| `SecretStore` | Plaintext ⇄ the ciphertext blob a row stores |
| `SshSecurity` | Installs the full Bouncy Castle provider over Android's cut-down one |

**Generation writes what `ssh-keygen` writes.** Ed25519 comes out as an OpenSSH v1 block,
RSA as a PKCS#1 PEM, and the public half as the single line a server's `authorized_keys`
wants. Generated pairs carry **no passphrase** — at rest they are protected by the
`SecretStore`, which is what actually guards the database file.

**Import goes through SSHJ**, the same library the transport will authenticate with, so a
key that imports successfully is one that will actually connect. The public half is
*derived from the private material* rather than trusted from a separate `.pub` file. That
also covers the formats a hand-rolled parser would miss: PKCS#1, PKCS#8, PuTTY, and
bcrypt-KDF-encrypted OpenSSH v1 blocks. `KeyImporter.isEncrypted` inspects the text
(`Proc-Type: 4,ENCRYPTED`, `BEGIN ENCRYPTED PRIVATE KEY`, or a non-`none` cipher name in an
OpenSSH v1 block) so the dialog can show the passphrase field *before* the user commits to
an import that would fail.

`KeyPairFactory.generate` runs on `Dispatchers.Default` from the ViewModel — RSA generation
is seconds of CPU and must not touch the main thread.

### `SecretStore` — how secrets are sealed at rest

`KeystoreSecretStore` is the implementation the app wires up. It seals every secret with
**AES-256/GCM** under a key generated in the **Android Keystore** (alias
`com.bam.sshfs.secrets`), created on first use and never exportable — the app can ask the
Keystore to encrypt and decrypt, but can never read the key itself. Rooting or pulling the
database therefore yields blobs, not credentials.

The stored blob is `v1:` + Base64(12-byte IV ‖ ciphertext+GCM tag). Two consequences worth
knowing:

- **The IV is per-call and chosen by the Keystore** (`setRandomizedEncryptionRequired`), so
  two identities with the same password produce different rows, and GCM's never-reuse-an-IV
  rule can't be broken by a call-site mistake.
- **The `v1:` prefix is the format handle.** A blob *without* it predates encryption and is
  read back as plain Base64, so rows written by earlier builds keep working; they are
  re-sealed the next time that secret is written. A future scheme (a
  user-authentication-gated key, say) gets its own prefix rather than an ambiguous blob.

Failures — a truncated blob, a tampered one that fails the GCM tag, or a Keystore key wiped
by a device-credential reset — surface as `SecretStoreException` and reach the user through
the ViewModel's error flow. A wiped key is unrecoverable by design: the affected key or
password must be re-entered.

`PassthroughSecretStore` remains, but only as the **base64, not encryption** stand-in for
unit tests, which run on a desktop JVM with no `AndroidKeyStore` provider. The real
round-trip is covered by the instrumented test in `app/src/androidTest/…/crypto/`.

A biometric / device-credential gate before unlocking is not wired up yet — see `TODO.toml`.

## UI — editing secrets you can't read back

`IdentityForm` (in `…/ui/identities/`) is the editable state of one identity, kept free of
Compose and Android types so its rules are plain unit tests. Two decisions live in it:

- **A stored password is never decrypted into the UI.** The form's `password` field carries
  an *intent*, not a value: `null` leaves the stored ciphertext untouched, `""` clears it,
  and anything else replaces it. `IdentitiesViewModel.passwordFor` is the only place that
  resolves the three cases against the existing row.
- **An identity must be usable.** `validate()` rejects a blank name or username, and rejects
  a draft with neither a password nor a key — the schema allows that row, but nothing could
  ever connect with it, so the editor won't create one.

`HostForm` (in `…/ui/hosts/`) follows the same shape — a plain data class, unit-tested:

- **The port stays a string while it's being typed.** A numeric field can't express "blank
  means the default", which is what the user almost always wants; `effectivePort` resolves an
  empty field to `DEFAULT_SSH_PORT` and `validate()` only complains about a *typed* port that
  isn't a real TCP port.
- **Extra arguments are validated, not interpreted.** The editor won't save a line the
  transport couldn't act on, but it doesn't police *which* ssh options are allowed.

### Extra connect arguments live in `…/net/ExtraArgs`

The freeform per-host options are `ssh_config`-style: one `Option value` per line, `#`
comments and blank lines ignored, `space`/`tab`/`=` all accepted as the separator. Parsing
sits in `…/net/` rather than the UI package because the transport reads the same text when it
opens a connection — the editor and the connection must never disagree about what a line
means.

### "Test connection" stops before authentication

`ConnectionProbe` opens an SSHJ transport, records the server's version banner and host-key
fingerprint, and hangs up. It deliberately does **not** authenticate: credentials are
decrypted by the connection manager, not the UI, and what the user needs while typing an
address is whether that address answers at all. A host setting `ProxyJump` is probed
*directly* and the result says so, since jump handling belongs to the transport task.

### The shell is an enum, and connection state is a singleton

`Destination` (in `…/ui/shell/`) enumerates the four sections with their label and icon, so
the nav bar is generated from it — adding a section is one enum entry, not a second list to
keep in sync. `AppShell` owns only the nav bar; each section keeps its own `Scaffold`, top
bar and FAB, which is why switching tabs can throw the section composable away: the state
that matters lives in Room and in `ConnectionRegistry`.

`ConnectionRegistry` (in `…/net/`) is a process-wide `StateFlow` of host id → state, held
outside any ViewModel because the connection manager service — not the UI — will own the real
sessions, and the nav-bar badge, the connections list, and later the `DocumentsProvider`'s
roots all read the same map. Until that service lands, `ConnectionsViewModel` writes to the
registry using the `ConnectionProbe` handshake, so *connected* means reachable, not
authenticated.

## The transport — `…/net/ssh/`

SSHJ is wrapped, never used directly outside this package. The seam is **`SftpSession`**: a
blocking interface of exactly the operations SAF needs (`list`, `stat`, `canonicalize`,
`open`, `mkdir`, `rename`, `delete`) returning **`RemoteEntry`** and **`RemoteHandle`** —
plain values with no SSHJ types in them. The `DocumentsProvider` and the metadata cache work
in those, so the library stops at this package's edge and could be swapped without touching
them.

| File | What it owns |
|------|--------------|
| `SftpSession.kt` | The interface, plus `RemoteEntry` / `RemoteHandle` |
| `SshjSftpSession.kt` | The SSHJ implementation, and attribute → `RemoteEntry` mapping |
| `SshConnector.kt` | The whole connect recipe: options, verification, jump chain, auth |
| `SshOptions.kt` | The `ssh_config` options this layer honours, parsed from `ExtraArgs` |
| `KnownHosts.kt` | The host-key trust store (in-memory and file-backed) and fingerprints |
| `TofuHostKeyVerifier.kt` | Trust-on-first-use verification |
| `ReconnectingSession.kt` | Re-dials underneath a live session; `RetryPolicy` |
| `SshCredentials.kt` | Decrypted credentials, and the `CredentialResolver` that opens them |
| `SshTransportException.kt`, `ErrorMapping.kt` | Failure classification |
| `RemotePaths.kt` | POSIX path arithmetic for remote paths |

### Host keys are trusted on first use, and a change is never silent

`TofuHostKeyVerifier` remembers a fingerprint the first time an address answers and demands
the same key afterwards. A **changed** key is always refused — that is the one attack
host-key checking exists to catch — and clearing it is a deliberate user action
(`KnownHostsStore.forget`), never something the transport does for itself. Setting
`StrictHostKeyChecking yes` in a host's extra arguments turns first contact into a refusal
too. Fingerprints are OpenSSH's `SHA256:…` spelling, so they can be compared against what
`ssh-keyscan` prints.

The store is a **plain text file**, one `host:port SHA256:…` line per entry, rather than a
Room table: it holds no secrets, it is the artefact a user might want to inspect or delete by
hand, and the transport must be able to read it without the database open.

### Which `ssh_config` options actually do something

`SshOptions.from` reads the host's extra-argument text (parsed by
[`ExtraArgs`](#extra-connect-arguments-live-in-netextraargs)) and honours `ProxyJump`,
`ConnectTimeout`, `ServerAliveInterval`, `Compression`, and `StrictHostKeyChecking`.
Everything else is collected in `SshOptions.ignored` rather than dropped, so the UI can say a
line had no effect instead of leaving the user to guess. `ProxyJump` takes a comma-separated
`[user@]host[:port]` chain; each hop is dialled *through* the previous one over a
`direct-tcpip` channel, and closing the session tears the chain down in reverse so a jump host
never goes away before what tunnels through it.

### Credentials are decrypted for the length of a handshake

`CredentialResolver` turns an `Identity` and its `SshKey` into a short-lived `SshCredentials`
using the same [`SecretStore`](#secretstore--how-secrets-are-sealed-at-rest) the editors write
through. Nothing holds one across connections. Authentication tries the **key first, then the
password** — the order `ssh` itself prefers — so an identity carrying both still connects when
the server hasn't got the key.

### Failures are classified, because only some are worth retrying

Every method throws `SshTransportException` carrying an `SshFailure`: `NETWORK`,
`AUTHENTICATION`, `HOST_KEY_UNKNOWN`, `HOST_KEY_CHANGED`, or `REMOTE`. `ReconnectingSession`
retries **only** `NETWORK` — it closes the dead session, re-dials through the connector, and
runs the call again with exponential backoff (`RetryPolicy`, three attempts by default).
Anything the server actually answered, such as a missing file or a rejected password, passes
straight through: retrying it would repeat the same answer, and retrying a rejected password
would lock the account out. The session is connected **lazily** on first use and guarded by a
lock, because the `DocumentsProvider` calls in from several handler threads at once.

## Data flow — SAF and the transport

*To be filled in as the DocumentsProvider and connection service land (see `TODO.toml`).*
