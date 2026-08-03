# sshfs_android

An Android app that exposes remote SSH/SFTP servers to the rest of the device as browsable
storage, via the Storage Access Framework. See `CLAUDE.md` for what the project is and
`docs/architecture.md` for how it works internally; active work lives in `TODO.toml`.

> **Not a real mount.** Android gives unprivileged apps no FUSE mount, so there is no
> mount point and no `/mnt/...` path — SAF-aware apps (the system Files app and most
> "open"/"save" dialogs) see the remote files; apps that demand a raw file path do not.

## Status

Early. The app builds, installs, and opens on the **Keys** screen, which is fully working:
generate a pair on-device, import one you already use, and copy the public key out. The
Identities and Hosts screens, the app shell that navigates between all three, the SSH
transport, and the `DocumentsProvider` are still to come.

> **Keys are not yet encrypted at rest.** Private keys currently sit in the app's private
> database base64-encoded, not Keystore-encrypted — that lands with the credential-storage
> task in `TODO.toml`. Don't import a key you can't afford to lose control of yet.

## Managing keys

The Keys screen is the first thing the app opens. **Add key** offers two routes:

- **Generate** — name the pair, optionally give it a comment (appended to the public key
  exactly as `ssh-keygen` does), and pick **Ed25519** (recommended) or **RSA 3072-bit**.
  The pair is created on the device; the private half never leaves it.
- **Import** — paste an existing private key, or pick the file with the system file
  picker. Passphrase-protected keys are detected as you paste, and a passphrase field
  appears; PEM (PKCS#1/PKCS#8), PuTTY, and OpenSSH v1 blocks are all accepted. The public
  key is derived from the private material, so you don't need the matching `.pub` file.

Each stored key shows its algorithm, where it came from, and its `SHA256:` fingerprint.
The **More** menu on a key offers:

- **Show public key** — the full `ssh-ed25519 AAAA… comment` line, with **Copy** to put it
  on the clipboard for pasting into a server's `~/.ssh/authorized_keys`.
- **Rename** — the only editable field on a stored pair.
- **Delete** — refused with a count when identities still use the key, and offered again as
  **Unlink and delete**, which clears those links first.

## Managing identities

An **identity** is how you sign in: a username plus a password, a stored key, or both (the
key is tried first and the password acts as the fallback). Keeping identities separate from
hosts means the same login can serve several servers without re-entering it.

**Add identity** asks for a display name, the username, an optional password, and an
optional key picked from the Keys screen. Saving is refused until the identity has a name, a
username, and at least one credential — an identity with neither password nor key can't
connect, so the editor won't create one.

A stored password is never shown again. When you edit an identity that has one, the password
field starts empty and reads *"A password is stored"*; type to replace it, or use **Remove
stored password** to clear it. Each identity in the list shows its username and which
credentials it carries.

The **More** menu offers **Edit** and **Delete**. Deleting is refused with a count when hosts
still default to that identity, and offered again as **Unlink and delete**, which clears
those defaults first.

> The Identities screen is not reachable from the UI yet — the app still opens straight onto
> Keys. The bottom navigation over Hosts / Identities / Keys lands with the app-shell task in
> `TODO.toml`.

## Requirements

Nothing on the host but Docker — the JDK, Android SDK, and Gradle all live in the
containerized pipeline at `/data/android` (see `docs/tools.md`). Do **not** build against
a host toolchain.

## Build

```sh
BAM_STORE_PUBLISH=0 /data/android/build.sh /path/to/sshfs_android
```

Defaults to `:app:assembleDebug`; pass another Gradle task as a second argument
(`:app:lint`, `:app:test`, `:app:assembleRelease`). The APK lands in
`app/build/outputs/apk/debug/app-debug.apk`. Drop `BAM_STORE_PUBLISH=0` to also publish
the build to the BAM Store.

## Run on the emulator

```sh
cd /data/android
S=.claude/skills/android-dev/scripts/emulator.sh
"$S" up                                              # boot the headless emulator
"$S" install /path/to/sshfs_android/app/build/outputs/apk/debug/app-debug.apk
"$S" launch com.bam.sshfs
"$S" screenshot /tmp/screen.png                      # see what it looks like
```

## Project facts

| | |
|---|---|
| Application id | `com.bam.sshfs` |
| `minSdk` | 26 — the floor for `StorageManager.openProxyFileDescriptor` |
| `compileSdk` / `targetSdk` | 35 |
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Gradle | 8.9 via the checked-in wrapper; AGP 8.5.2, Kotlin 1.9.24 |
