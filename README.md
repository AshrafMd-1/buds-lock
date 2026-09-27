# Buds Lock

Buds Lock is an independent Android companion for OnePlus Buds 4. Its single
purpose is Sleep Lock: temporarily set the confirmed single-, double-, and
triple-tap mappings on both earbuds to `None`, then restore the exact original
values later. It does not require HeyMelody at runtime and contains no
repackaged or decompiled vendor code.

This is an unofficial community project, not affiliated with or endorsed by
OnePlus, OPPO, or HeyMelody.

## What version 2.0 provides

Version 2.0 is the stable app-and-widget release milestone. It consolidates the
verified Sleep Lock behavior and the final transparent-widget visual states;
it does not expand or change the confirmed Bluetooth protocol scope.

- Blocks exactly six mappings: left/right single, double, and triple tap.
- Leaves swipe, touch-and-hold, call controls, ANC, audio effects, and firmware
  features untouched.
- Provides a transparent icon-only 1×1 widget. System accent (blue fallback)
  means blocked, gray means normal or offline, and amber means restoration is
  needed. The app also removes the decorative icon circle for normal/offline
  states while retaining it for active and recovery states.
- Uses Android audio/profile signals for the gray offline presentation, while
  the confirmed RFCOMM command connection remains the final authority. An
  offline tap can safely retry the real transport; a genuine disconnect fails
  before any mapping read or write. Disconnection never changes the saved lock
  state or deletes recovery data.
- Uses Android dynamic colors on Android 12+ with a blue fallback.

## Safety invariants

1. Use only the device-confirmed Classic RFCOMM command UUID
   `0000079a-d102-11e1-9b23-00025b00a5a5`.
2. Read mappings with confirmed command `0x0108` and require all six known
   selectors before any mutation.
3. Atomically save all six originals before the first write. Never overwrite
   an existing backup.
4. Send only the confirmed single-record `0x0401` command. Every write must
   receive `0x8401` status `00`.
5. Publish **Controls blocked** only after a final read shows all six values as
   `FN=00`.
6. Publish **Controls normal** and clear the backup only after a final read
   exactly matches all six saved originals.
7. Treat interruption, partial failure, or malformed recovery data as a
   recoverable stop condition. Never guess a selector or command.

Do not uninstall Buds Lock or clear its storage while controls are blocked.
That would delete the local restoration snapshot. If this happens, HeyMelody
can still configure the gestures manually.

## Install and use

The production APK is distributed separately from the source archive. Because
the old v0.5 test build used a debug certificate, it cannot be upgraded in
place to the production certificate:

1. In v0.5, restore controls and confirm the widget/app is gray and says
   **Controls normal**.
2. Uninstall v0.5.
3. Install `BudsLock-v2.0.apk` and grant **Nearby devices**.
4. Connect the OnePlus Buds 4 in Android Bluetooth settings and select them in
   Buds Lock once.
5. Add the **Buds Lock** 1×1 widget.

An existing production-signed 1.x installation can be upgraded in place with
`adb install -r BudsLock-v2.0.apk`. This preserves the selected device, current
state, and any recovery snapshot because version 2.0 uses the same package and
production signing certificate.

When Android reports the Buds offline, the widget is gray with a Bluetooth-off
icon. A tap safely retries the real command transport: a genuine disconnect
fails before any mapping read or write, while an OEM connection-status false
negative can still complete the requested block/restore operation. When Android
reports the reconnection, the widget returns to the last verified state.

HeyMelody can remain installed. Only one app can reliably own the RFCOMM
command socket at a time, so close HeyMelody if Android reports a connection
failure. Buds Lock opens the socket only for one short verified transaction and
then closes it.

## Architecture

- `protocol/`: platform-independent OPOv1 framing and mapping parsing.
- `core/`: selector policy and pure UI state resolution.
- `BudsRfcommClient`: Classic Bluetooth transport only.
- `SleepLockTransaction`: Android-independent save/write/verify/restore policy.
- `SleepLockController`: Android connection, status, and error bridge.
- `SleepLockService`: non-exported foreground bridge used by app and widget.
- `MainActivity` and `BudsLockWidgetProvider`: presentation only; they do not
  encode or send protocol packets.

The evidence trail and packet documentation are in [`docs/`](docs/). Version
2.0 also rejects duplicate required selectors, limits device selection to
OnePlus Buds 4 (or the address protected by an existing recovery snapshot), and
adds deterministic interruption/retry tests for both lock and restore.

## Build and test

Requirements: JDK 17 and Android SDK Platform 35 with Build Tools 35.0.0.
Android Studio can open the project normally. Run the dependency-free protocol
and state tests with:

```bash
./verify-protocol.sh
```

To produce a signed release without Gradle, set the signing variables and run:

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export BUDS_LOCK_KEYSTORE=/private/path/BudsLock-release.p12
export BUDS_LOCK_KEY_ALIAS=budslock
export BUDS_LOCK_STORE_PASSWORD='your-store-password'
export BUDS_LOCK_KEY_PASSWORD='your-key-password'
./build-release.sh
```

The APK is written to `dist/BudsLock-v2.0.apk`. The repository intentionally
tracks that production APK, its public certificate, and public checksums so a
fresh clone contains the complete public release. Keep the private keystore and
passwords outside the repository and backed up securely; future Android updates
must be signed with the same key. See [`SIGNING.md`](SIGNING.md) for the public
certificate fingerprint and release-signing precautions.

## Current limits

- Tested with OnePlus Buds 4 and Android 16; other earbud models are not
  supported.
- The public A2DP/Headset connection signal can vary by Android vendor. The
  app also checks the exact Android audio route, but these signals affect only
  presentation. The confirmed RFCOMM socket and validated gesture read remain
  the authority for an operation; a genuine disconnect fails before mutation.
- ANC, battery, dual connection, EQ, spatial audio, firmware updates, and other
  HeyMelody features are outside version 2.0.

## Contributing

Read [`AGENTS.md`](AGENTS.md) before changing protocol or recovery code. New
commands or selector meanings require device evidence and documentation using
`CONFIRMED`, `HYPOTHESIS`, or `UNKNOWN`.
