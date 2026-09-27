# Reverse-engineering notes

## Evidence trail

1. `com.oplus.melody.ui.component.control.a` (`BaseEarControlFragment`) handles a changed preference and calls its update method.
2. That method reads row metadata (`action`, `button`, `showEar`), maps the selected label through `K8.a.a`, constructs `KeyFunctionInfoDTO`, and calls repository `u0(..., 1025)`.
3. `EarphoneRepositoryClientImpl.u0` packages the address, command, and records; `BluetoothService` forwards them to `HeadsetCoreService.Q0`.
4. `HeadsetCoreService.Q0` builds payload `1 + 4*N`, with count first and each `KeyFunctionInfo.copyIntoData` record next; it sends command `1025` and then polls command `264`.
5. `KeyFunctionInfo.copyIntoData` writes `deviceType`, `deviceButton`, `buttonAction`, `function` in that order.
6. `f7.a` parses/serializes command LE, sequence, payload length LE, payload. `v7.b` adds the `AA` OPOv1 frame.
7. `K8.a.a` maps resource ID `0x7f120537` (`...control_none`, text `None`) to function zero.
8. The receive parser for the refresh response reads count from response byte 1 and four-byte records from byte 2.
9. Android runtime logs and successful independent reads confirm Classic RFCOMM,
   command UUID `0000079a-d102-11e1-9b23-00025b00a5a5`, channel 15 / DLCI 30.
10. Independent `0x0108` reads returned 18 records. Changing only Left double-tap
    from Next track to None changed `01 01 02 06 → 01 01 02 00`; restoration
    returned it to `06`. Repeating on Right changed only
    `02 01 02 06 → 02 01 02 00`; restoration returned it to `06`.
11. The set-response parser handles `0x8401`, reads payload byte 0 as status,
    and treats zero as success. `HeadsetCoreService.Q0` then schedules `0x0108`.
12. Independent write validation succeeded on device. Set-None frame
    `AA 0C 00 00 01 04 01 05 00 01 02 01 02 00` received
    `AA 08 00 00 01 84 01 01 00 00`; read-back returned FN `00` and physical
    Right double-tap was disabled. Restore frame ending in `06` received the
    same success response; read-back and physical testing confirmed restoration.
13. A controlled Right single-tap change produced only
    `02 01 01 01 → 02 01 01 00`; it was then restored to Play/Pause.
14. A controlled Right triple-tap change produced only
    `02 01 03 05 → 02 01 03 00`; it was then restored to Previous track.
    Together with the independently established left/right `DT` values, these
    confirm the six selector tuples used by the v2.0 Sleep Lock scope.

## What is not established

- OnePlus Buds 4 product/model identifier and runtime support configuration.
- Exact `DT/DB/BA` tuples for slide, touch-and-hold, and call controls.
- Meanings of nonzero `0x8401` status codes and optional response byte 1.
- Full connection/authorization handshake.
- ANC and battery command IDs/formats.

## Minimal proof-of-concept design

1. Transport: use only the confirmed RFCOMM command UUID; retry the observed initial socket-release failure once without inventing a handshake.
2. Harmless read: send `0x0108` and parse all 18 records.
3. Single-record test: save `02 01 02 FN`, write the same selector with `FN=00`, require `0x8401` status zero, then issue `0x0108` and require an exact match. **Completed successfully.**
4. Restore: write the saved record and read back again. Make restoration repeatable.
5. Implement bulk lock as a transaction-like state machine:
   `Normal → Saving → Locking → Locked`; on partial failure remain
   `RecoveryNeeded`, never `Locked`. **Implemented and physically validated on
   OnePlus Buds 4:** all six taps were disabled, the exact originals were
   restored, and swipe/touch-and-hold remained outside the transaction.

Persist original mappings before the first write and never replace them while
locked. A disconnect changes only connection availability; it does not clear or
recreate recovery data. Restoration remains idempotent because each saved
record is compared before writing and the backup is cleared only after an exact
six-record read-back. If one earbud is absent, fail closed without reporting
Locked unless all six required selectors are returned.

## Android 16 connection-state validation

The target Realme Android 16 device produced a false offline presentation even
while its Bluetooth diagnostics showed the exact Buds identity address as
bonded, A2DP `CONNECTED`, Headset `CONNECTED`, active, and playing via LHDC.
Both list-based and direct public profile queries were therefore insufficient
as a write gate on this OEM stack.

The behavior retained in version 2.0 treats the public audio/profile signals as
presentation evidence only. It additionally checks the exact `AudioDeviceInfo` route, but a user tap
is allowed to try the confirmed RFCOMM command service directly. This does not
weaken the mutation transaction: a genuine disconnect fails during socket
connection before `0x0108` or `0x0401`; a successful socket still must return a
complete unique six-selector read before the durable backup and writes occur.
This behavior was validated successfully on the target device.
