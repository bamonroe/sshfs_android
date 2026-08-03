# sshfs_android

An Android app that exposes remote SSH/SFTP servers to the rest of the device as browsable
storage, via the Storage Access Framework. See `CLAUDE.md` for what the project is and
`docs/architecture.md` for how it works internally; active work lives in `TODO.toml`.

> **Not a real mount.** Android gives unprivileged apps no FUSE mount, so there is no
> mount point and no `/mnt/...` path — SAF-aware apps (the system Files app and most
> "open"/"save" dialogs) see the remote files; apps that demand a raw file path do not.

## Status

Early scaffold. The app builds, installs, and launches a Compose/Material 3 placeholder
screen. The Hosts / Identities / Keys UI, the SSH transport, and the `DocumentsProvider`
are still to come.

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
