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
| `…/data/settings/` | `AppSettings`: the user's preference switches (no secrets) |
| `…/crypto/` | Key generation, import/parsing, OpenSSH text formats, secret storage, the authentication gate |
| `…/ui/` | Compose screens and the Material 3 theme |
| `…/ui/keys/` | The Keys screen: list, generate, import, show/copy public key |
| `…/ui/identities/` | The Identities screen: list, editor form + validation, delete/unlink |
| `…/ui/hosts/` | The Hosts screen: list, editor form + validation, connection test |
| `…/ui/connections/` | The Connections screen: per-host state and the connect/disconnect controls |
| `…/ui/settings/` | The Settings screen: the authentication-gate switch and its re-seal pass |
| `…/ui/shell/` | The single-activity shell: the `Destination` enum and its bottom nav bar |
| `…/net/` | Transport-facing helpers, plus the connection manager and its foreground service |
| `…/net/ssh/` | The SSH/SFTP transport: connect, host-key trust, remote file operations |
| `…/provider/` | The `DocumentsProvider`: SAF roots, document ids, cursors, and the SFTP workers |
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

The stored blob is `<prefix>` + Base64(12-byte IV ‖ ciphertext+GCM tag), where the prefix says
which key sealed it: `v1:` the plain key, `v2:` the authentication-gated one (below). Two
consequences worth knowing:

- **The IV is per-call and chosen by the Keystore** (`setRandomizedEncryptionRequired`), so
  two identities with the same password produce different rows, and GCM's never-reuse-an-IV
  rule can't be broken by a call-site mistake.
- **The prefix is the format handle.** A blob *without* one predates encryption and is
  read back as plain Base64, so rows written by earlier builds keep working; they are
  re-sealed the next time that secret is written. Each scheme gets its own prefix rather than
  an ambiguous blob, and *reads* dispatch on the prefix alone — never on the current setting —
  so flipping the setting can never orphan a row.

Failures — a truncated blob, a tampered one that fails the GCM tag, or a Keystore key wiped
by a device-credential reset — surface as `SecretStoreException` and reach the user through
the ViewModel's error flow. A wiped key is unrecoverable by design: the affected key or
password must be re-entered.

`PassthroughSecretStore` remains, but only as the **base64, not encryption** stand-in for
unit tests, which run on a desktop JVM with no `AndroidKeyStore` provider. The real
round-trip is covered by the instrumented test in `app/src/androidTest/…/crypto/`.

### The authentication gate — `v2:` blobs, and who may prompt

With **Settings → Require authentication** on, secrets are sealed under a *second* Keystore key
(alias `com.bam.sshfs.secrets.gated`) built with `setUserAuthenticationRequired(true)`, and the
blobs get the `v2:` prefix. Both keys stay present; only the prefix decides which one opens a
given row.

Four decisions hold this together:

- **The gate is time-bound, not per-use.** The gated key is created with
  `setUserAuthenticationParameters(300s, BIOMETRIC_STRONG | DEVICE_CREDENTIAL)` (and the
  deprecated `setUserAuthenticationValidityDurationSeconds` below API 30). One prompt therefore
  covers the connect, the re-dials and the provider's reads for five minutes. The alternative —
  an auth-per-use key — would mean threading a `BiometricPrompt.CryptoObject` through
  `CredentialResolver` and the SFTP transport, and would re-prompt on every reconnect from a
  background thread that has no Activity to prompt from.
- **`DEVICE_CREDENTIAL` is always allowed**, so a device with a PIN but no enrolled fingerprint
  can still use the gate. When the device has neither, `BiometricAuthGate.canAuthenticate`
  is false and the switch is disabled — a gated key can't even be generated there.
- **Prompting is decoupled from decrypting.** `SecretAuthGate` is a process-wide holder for
  whatever can currently show a prompt; `MainActivity` registers a `BiometricAuthGate` in
  `onStart` and clears it in `onStop`. Code that hits a locked key runs on a binder thread or
  inside the connection service and can't reach an Activity, so it asks the holder. With nothing
  registered, `authenticate` returns false immediately rather than blocking on a dialog no one
  can see, and the caller gets `AuthenticationRequiredException` — a *recoverable* failure,
  distinct from `SecretStoreException`.
- **Connecting prompts up front.** `ConnectionManager.open` checks
  `KeystoreSecretStore.needsAuthentication(...)` on the identity's and key's blobs and opens the
  window *before* the handshake, so the dialog lands on the connect the user just asked for
  rather than surfacing as a mid-listing failure. Sealing needs the window open too (the gated
  key requires authentication to encrypt as well), which is why the editor ViewModels call
  `Secrets.unlockForWrite` before saving a key or a password.

Flipping the setting runs `SecretResealer`, which walks both tables and rewrites every blob
under the new scheme — otherwise "require authentication" would only apply to secrets saved
after the switch. It runs while the user is still authenticated, because converting a `v2:` blob
back means reading it. Rows are converted one at a time and a blob that won't open is *left
alone* and counted as a failure: overwriting it would destroy the only copy. A part-finished
pass is therefore safe — both prefixes still read, and re-running finishes the job.

`Secrets.store(context)` is the single place that builds the app's `SecretStore`, binding its
write scheme to `AppSettings.requireAuthentication`. The setting itself lives in plain
`SharedPreferences` (`…/data/settings/AppSettings`) — it is a switch, not a secret — and is read
synchronously because every seal consults it from an arbitrary thread.

The gated round-trip has **no automated test**: it needs a real enrolled credential and a user
tapping the prompt, which the emulator harness can't supply. What is covered is the part that
can be: prefix dispatch and `needsAuthentication` in the unit tests, and `SecretAuthGate`'s
register/prompt/handoff logic with a fake gate.

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

`Destination` (in `…/ui/shell/`) enumerates the sections with their label and icon, so
the nav bar is generated from it — adding a section is one enum entry, not a second list to
keep in sync. `AppShell` owns only the nav bar; each section keeps its own `Scaffold`, top
bar and FAB, which is why switching tabs can throw the section composable away: the state
that matters lives in Room and in `ConnectionRegistry`.

`ConnectionRegistry` (in `…/net/`) is a process-wide `StateFlow` of host id → state, held
outside any ViewModel because `ConnectionManager` — not the UI — owns the real sessions, and
the nav-bar badge, the connections list, and later the `DocumentsProvider`'s roots all read
the same map. `ConnectionsViewModel` therefore only *sends commands* and *renders the
registry*; it never touches a session.

## Keeping connections alive — `…/net/`

| File | What it owns |
|------|--------------|
| `ConnectionManager.kt` | The live sessions, one per connected host; connect / disconnect |
| `ConnectionService.kt` | The foreground service that keeps the process (and the sessions) alive |
| `ConnectionNotification.kt` | The ongoing notification, its channel, and the disconnect-all action |
| `ConnectionRegistry.kt` | The state the UI renders |
| `SafRoots.kt` | The SAF authority and the roots-changed notification |

**Lifetime and sessions are deliberately separate.** `ConnectionService` exists only so
Android won't kill the process while another app browses a root — a bound service would die
with the UI and a plain background service is killed within minutes. The sessions themselves
live in `ConnectionManager`, a process-wide singleton, so restarting the service reconnects
nothing that is already up and the `DocumentsProvider` can look a session up from a binder
thread without binding to anything. The service is `START_NOT_STICKY`: a restart with no
sessions and a stale notification is worse than nothing, and the UI re-issues a connect when
the user asks for one. It **stops itself** when the last host disconnects, so there is no
ongoing notification without a reason for it.

Commands go in as intents (`ACTION_CONNECT` / `ACTION_DISCONNECT` / `ACTION_DISCONNECT_ALL`
with a host id) rather than through a binder, which keeps the notification action and the UI
on exactly one path. `startForeground` is called from `onCreate`, before any dialling: Android
gives a started service only seconds to promote itself, and a handshake takes far longer.

**A connect resolves credentials, not just an address.** `ConnectionManager` reads the host's
`defaultIdentityId` (a host without one fails with that as the reason), decrypts through
`CredentialResolver`, and wraps the result in a `ReconnectingSession` — so the credentials are
re-resolved on every re-dial and a rotated password takes effect without a reconnect by hand.
Touching `serverVersion` forces the first handshake, so a bad credential fails at the Connect
button instead of at the picker's first directory listing. Failures are recorded in the
registry rather than thrown: the caller is a service command with no stack to unwind into.

Every transition — connected, disconnected, all-disconnected — calls
`SafRoots.notifyChanged`, which is `ContentResolver.notifyChange` on
`content://com.bam.sshfs.documents/root`. The authority constant lives in `SafRoots` and must
match the provider's `android:authorities` when it lands; until then the notification simply
reaches no observers.

`ConnectionProbe` (the host editor's "Test connection") is unchanged and still
unauthenticated — see below.

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

## The SAF provider — `…/provider/`

`SshfsDocumentsProvider` is the whole seam between the remote servers and the rest of the
device. It is registered in the manifest under the authority **`com.bam.sshfs.documents`**
(the same string `SafRoots.AUTHORITY` holds) with the `DOCUMENTS_PROVIDER` intent filter and
the `MANAGE_DOCUMENTS` signature permission, so only the system can bind it directly while any
app's picker can still reach it through SAF.

| File | What it owns |
|------|--------------|
| `SshfsDocumentsProvider.kt` | The SAF entry points: roots, listings, open, create, delete, rename |
| `DocumentId.kt` | The `hostId:/absolute/path` encoding and its containment tests |
| `DocumentCursors.kt` | The root and document column projections, and row building |
| `DocumentMimeTypes.kt` | Extension → MIME type, and the directory MIME constant |
| `DocumentDescriptors.kt` | Proxy-FD vs. cached-temp-file strategy for `openDocument` |
| `RemoteProxyCallback.kt` | The `ProxyFileDescriptorCallback` that turns reads/writes into SFTP ranges |
| `DocumentMode.kt` | SAF's mode string parsed into open flags, as a pure value |
| `DocumentNames.kt` | Collision-free naming (` (1)`, ` (2)`…) and extension rules |
| `MetadataCache.kt` | The short-TTL listing/stat cache, keyed by host and path |
| `RemoteWorkers.kt` | One single-threaded executor per host, so SFTP never runs on a binder thread |

### The provider holds no state

Every session lives in `ConnectionManager`; the provider only reads it. That matters because
Android creates a provider instance on a *cold* binder call — a picker can query roots long
after the last activity was destroyed — and a provider with its own session cache would answer
from a copy the UI had already invalidated. `queryRoots` walks
`ConnectionManager.connected()`, so the picker and the Connections screen can never disagree.

### Document ids are `hostId:/absolute/path`

`DocumentId` owns the encoding. SAF hands ids back opaquely and with no other context, so an
id has to name its own host; the split is on the **first** colon, which leaves a colon inside
a remote path intact. `isChildDocument` and the breadcrumb walk are answered by
`DocumentId.contains` — pure string arithmetic, because SAF asks those far too often to spend
a round trip on.

### Roots are connected hosts only

A disconnected host produces **no** root rather than an unavailable one: a root the picker
cannot open is worse than no root at all. `ConnectionManager` calls `SafRoots.notifyChanged`
whenever the set changes, and `queryRoots` sets that same URI as the cursor's notification URI,
so an open picker updates itself as hosts come and go.

The root's document id needs an absolute path, but `Host.remoteRoot` may be `.` or `~/…`.
Canonicalizing costs a round trip, so it happens **once at connect time** and is cached on
`ConnectionManager.ConnectedHost.rootPath`; `queryRoots` is called often and must stay free.

### SFTP never runs on a binder thread

`RemoteWorkers` keeps one **single-threaded** executor per host and every remote call is
submitted to it and waited on with a 30-second timeout. Two reasons, both load-bearing: a
stalled TCP read on a binder thread burns one of the process's few binder threads and can wedge
unrelated SAF clients, and an SFTP channel is not safe for concurrent use. Per-host rather than
a shared pool means one slow server can't starve the others. The worker is shut down in
`ConnectionManager.disconnect`.

Failures are mapped to `FileNotFoundException`, which is what SAF's contract allows: the
picker shows an unavailable item instead of crashing the app that called in.

### Files stream; they are not downloaded

`openDocument` returns a descriptor from `StorageManager.openProxyFileDescriptor`, backed by
`RemoteProxyCallback`. Every `read`/`write` the calling app makes becomes an offset-addressed
SFTP request for exactly that range, so opening a multi-gigabyte file costs one `open` round
trip and nothing more until something reads. This is the closest an unprivileged Android app
gets to what SSHFS does with FUSE, and it is the reason the whole `RemoteHandle` seam exists.

`DocumentDescriptors` owns the choice of strategy. The proxy path needs the platform's
FUSE-backed implementation, which exists since API 26 but is *absent at runtime* on some
devices and emulator images, and there is no way to ask in advance — so the fallback is chosen
by catching `UnsupportedOperationException`, not by checking an API level. The fallback caches
the file under `cacheDir/saf-cache`, hands back a plain descriptor, and uploads it again from
the close listener; it is correct everywhere and much worse for large files, which is why it is
second.

Each open file gets its **own** handler thread, because a callback blocks on the network and
one app streaming a large file must not stall another's reads. Those threads still hand every
call to the host's `RemoteWorkers` executor, so the SFTP channel stays single-threaded.
Failures leave the callback as `ErrnoException` (`EIO`): the kernel turns that into the calling
app's `IOException`, whereas an unchecked exception there would take down the process.

`DocumentMode` parses SAF's mode string into open flags as a pure value, so a plain JVM test
covers the case that can destroy data — a bare `w` **truncates**, matching
`ParcelFileDescriptor` and every other provider, while `wa` keeps the contents.

### Creating, deleting and renaming

`createDocument`, `deleteDocument` and `renameDocument` all run on the host worker and all
notify the parent's child-documents URI afterwards, so an open picker re-lists itself.

Names are the subtle part, and `DocumentNames` owns them. SAF's contract is that
`createDocument` returns a document that **exists**, so a collision is resolved by the provider
rather than reported: the parent is listed once and the name counts up as ` (1)`, ` (2)`, …
the way Android's own providers do, so two apps saving `download.pdf` can't overwrite each
other. A leading dot is never treated as an extension — `.bashrc` is a whole name on a Unix
server. A rename that omits the extension keeps the original one.

### Listings and stats are cached for a few seconds

`MetadataCache` sits between the provider and the workers, keyed by host **and** path. A
picker showing one directory calls `queryChildDocuments` once and then `queryDocument` for
every row, and re-runs the lot on redraw; every one of those is an SFTP round trip otherwise. A
listing also seeds the stat of every child it saw, which is exactly the set of follow-up
queries the picker is about to make, so a directory view costs **one** remote call.

The TTL (`MetadataCache.DEFAULT_TTL_MILLIS`, 5s) is deliberately short rather than clever:
entries expire on their own, so a change made by someone else on the server is hidden for at
most a moment and the cache never has to stay right for long. Our *own* writes don't wait for
it — `createDocument`/`deleteDocument`/`renameDocument` invalidate the parent directory through
the same `notifyChildrenChanged` hook that nudges the picker, opening a file for write drops
that path's stat (its size is about to change and nothing tells us when it stops), and
`ConnectionManager` clears the whole host on disconnect, since a later reconnect may not even
be the same machine. The uniquify listing inside `createDocument`/`renameDocument` deliberately
bypasses the cache: a stale listing there would let two apps overwrite each other.

### Capability flags are promises

Flags are derived per entry from the `readable`/`writable` bits `RemoteEntry` carries, never
advertised unconditionally: SAF treats a flag as a commitment and shows the user a failed
operation if it isn't kept. Delete and rename are properties of the *parent* directory in
POSIX; the parent isn't in hand while building a row, so the entry's own writability stands in
as the closest available proxy. Roots add `SUPPORTS_CREATE`, which is what lets another app
pick a server as a save destination.

## Tests — what is covered where, and why

How to *run* both suites is in `README.md`; this section owns the shape of them.

| Suite | Lives in | Covers |
|-------|----------|--------|
| Unit (JVM) | `app/src/test/` | The data model and enum converters, the crypto layer, the pure SAF value types (`DocumentId`, `DocumentMode`, `DocumentNames`, `MetadataCache`), the form validation, `ExtraArgs`/`SshOptions`/`KnownHosts`/`RemotePaths`, and `SshjSftpSession` |
| Instrumented | `app/src/androidTest/` | `KeystoreSecretStore` against the real `AndroidKeyStore`, and the `DocumentsProvider` through `ContentResolver` |

Two decisions shape it:

**The SFTP wrapper is tested against a real server, not a mock.** `EmbeddedSftpServer`
(in the unit sources) starts Apache MINA SSHD in the test JVM over a temp directory and
hands back an `SshjSftpSession` connected to it. What that wrapper *does* is translate —
SFTP attributes into `RemoteEntry`, seconds into milliseconds, open flags into `OpenMode`,
SSHJ's failures into `SshTransportException` — and a mocked `SFTPClient` would only assert
that we call the methods we already decided to call. The server binds an ephemeral port and
needs no network access.

**The SAF tests go through the binder, and stub the transport.** The provider is exercised
by `ContentResolver`/`DocumentsContract` calls rather than by touching the class directly,
because the contract SAF holds us to is the cursors, document ids, and descriptors that come
back out of the resolver. Underneath, `ConnectionManager.adopt` publishes a `FakeSftpSession`
— an in-memory tree — as a connected root; that method exists **only** for these tests, and
keeps them off any real server. So the two suites meet in the middle: the transport is real
where the provider is faked, and faked where the provider is real.
