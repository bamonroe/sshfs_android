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
| `…/net/` | Transport-facing helpers: `ExtraArgs` parsing and the `ConnectionProbe` |
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

### `SecretStore` is a placeholder until the Keystore task

`PassthroughSecretStore` is **base64, not encryption**. It exists so the repository, the
ViewModel, and the schema already speak in ciphertext blobs; the credential-storage task
swaps in the Keystore-backed implementation without touching a call site. Until then, the
private key column is *not* actually protected — see `TODO.toml`.

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

## Data flow — SAF and the transport

*To be filled in as the DocumentsProvider, transport, and connection service land (see
`TODO.toml`).*
