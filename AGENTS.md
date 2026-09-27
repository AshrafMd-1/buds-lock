# Repository instructions

These rules apply to every change in this repository.

## Safety boundaries

- Never guess Bluetooth UUIDs, commands, packet fields, selector values,
  acknowledgement meanings, checksums, authentication, or encryption.
- Label protocol knowledge `CONFIRMED`, `HYPOTHESIS`, or `UNKNOWN` and record
  the evidence in `docs/`.
- Do not add HeyMelody APKs, split APKs, decompiled vendor sources, OnePlus
  artwork, private device logs, MAC addresses, signing keys, or credentials.
- Version 2.0 may mutate only the six selectors defined by
  `core/GestureSelectors`: DB `01`, BA `01/02/03`, DT `01/02`; only FN may be
  changed to `00` for lock or its saved original value for restore.
- UI, widget, and broadcast receivers must delegate mutations through the
  non-exported `SleepLockService` and `SleepLockController`.

## Recovery invariants

- Persist a complete six-record snapshot before the first write.
- Never overwrite an existing recovery snapshot.
- A disconnect, process restart, widget refresh, app restart, or connection
  broadcast must never clear or recreate the snapshot.
- Clear recovery data only after all six restored values are read back exactly.
- Never report locked until all six `None` values are read back exactly.
- Fail closed when the confirmed RFCOMM command transport cannot connect, a
  required record is missing, an acknowledgement is nonzero, or a response is
  malformed. OEM audio/profile status is presentation evidence, not permission
  to write; the real command socket remains authoritative.

## Architecture

- Keep framing and parsing Android-independent under `protocol/`.
- Keep selector and presentation policy pure under `core/`.
- Keep Bluetooth I/O in `BudsRfcommClient`.
- Keep transaction policy in the Android-independent `SleepLockTransaction`;
  use `SleepLockController` only as the Android connection/status bridge.
  Activities and widgets must not construct packets.
- Connection availability and verified earbud configuration are separate state
  dimensions. Offline presentation must not rewrite lock state.

## Required checks

Before release:

1. Run `./verify-protocol.sh`.
2. Build the signed APK with `./build-release.sh`.
3. Verify APK signatures, package ID, version, permissions, exported
   components, and absence of debug flags or secrets.
4. Physically test connect, block all six taps, disconnect while blocked,
   reconnect, restore, and confirm swipe/touch-and-hold remain unchanged.

Do not weaken a safety invariant merely to make an operation appear successful.
