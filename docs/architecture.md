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
| `…/ui/` | Compose screens and the Material 3 theme |
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

## Data flow — SAF and the transport

*To be filled in as the DocumentsProvider, transport, and connection service land (see
`TODO.toml`).*
