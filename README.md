# sshfs_android

An Android app that exposes remote SSH/SFTP servers to the rest of the device as browsable
storage, via the Storage Access Framework. See `CLAUDE.md` for what the project is and
`docs/architecture.md` for how it works internally; active work lives in `TODO.toml`.

> **Not a real mount.** Android gives unprivileged apps no FUSE mount, so there is no
> mount point and no `/mnt/...` path — SAF-aware apps (the system Files app and most
> "open"/"save" dialogs) see the remote files; apps that demand a raw file path do not.

## Status

Early. The app builds, installs, and opens on the **Connections** tab, with bottom navigation
over Connections, Hosts, Identities and Keys. The Keys, Identities and Hosts screens are each
fully working; the SSH transport, the connection service, and the `DocumentsProvider` are
still to come, so "connecting" a host currently only checks that it answers.

## How your credentials are stored

Private keys, key passphrases and identity passwords are **encrypted at rest**. Each one is
sealed with AES-256/GCM under a key held in the **Android Keystore**, which the app can use
but never read or export, so the app's database holds only ciphertext even if the device is
rooted and the file is pulled off it. Everything lives in the app's private storage; nothing
sensitive is ever written to plain preferences.

Two things worth knowing:

- **Wiping your device lock screen wipes the Keystore key.** If you remove or reset your PIN,
  pattern or password, Android may discard the app's key, and the stored secrets become
  permanently unreadable. The app will tell you a secret can't be decrypted; the fix is to
  re-import the key or re-enter the password. Keep a backup of any key you can't regenerate.
- Secrets written by pre-0.2 builds were only base64-encoded. They are still readable, and
  are re-encrypted the next time you edit them — open and re-save any key or identity from
  an older install to have it properly sealed.

A biometric or PIN prompt before secrets are unlocked is planned but not implemented yet.

## Managing keys

**Add key** offers two routes:

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

## Managing hosts

A **host** is a server you want to browse. **Add host** asks for:

- **Name** — the label you'll see, and the name of this server's root in the file picker.
- **Address** and **Port** — the hostname or IP, and the port. Leave the port blank for the
  default, 22.
- **Default identity** — which login from the Identities screen this host uses, or none.
- **Remote directory** — where the root opens. Blank means the login directory.
- **Extra connect arguments** — freeform ssh options, one `Option value` per line, with `#`
  comments allowed. This is where a jump host goes:

  ```
  ProxyJump bastion.example.com
  ServerAliveInterval 30
  ```

  A line with a keyword but no value is flagged inline and blocks the save; which options
  are meaningful is left to the transport rather than policed by the editor.

**Test connection** dials the draft *without saving it*, so you can check an address as you
type. Success reports the server's version banner and its host-key fingerprint; failure
reports why — an unknown host name, a refused connection, or a timeout. The test stops
before signing in, so it tells you the server is reachable, not that your credentials work.
A host with `ProxyJump` set is dialled directly and the result says so.

The **More** menu on a host offers **Edit** and **Delete**. Nothing references a host, so
deleting only asks for a confirmation; the server itself is untouched.

## Getting around, and connecting

The bottom bar switches between four sections — **Connections**, **Hosts**, **Identities**
and **Keys**. Each keeps its own list and controls; the tab you're on survives rotation.

The **Connections** tab lists every host you've added with its current state — *Not
connected*, *Connecting…*, *Connected* (with the server's version banner), or *Failed* with
the reason. **Connect** and **Disconnect** are on each row, and the tab's icon carries a badge
counting the hosts that are up.

> **Connecting doesn't sign in yet.** Until the transport and connection-manager tasks land,
> **Connect** runs the same unauthenticated handshake as **Test connection** — a host shown as
> connected is reachable, not logged in, and nothing appears in the file picker yet.

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
