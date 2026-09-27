# HeyMelody / OnePlus Buds protocol notes

Status vocabulary: **CONFIRMED** means supported by APK code/resource evidence; **HYPOTHESIS** requires a device trace; **UNKNOWN** means not determined.

## Scope and input

- APK: `com.heytap.headset`, version `116.9.0` (`116009000`), SHA-256 `89368eb2b33abbec1c783b70439679eb0f0a9fea56706999122ab8a3d0ca13b5`.
- Static APK evidence has now been correlated with controlled OnePlus Buds 4 runtime reads on a Realme P3 Ultra running Android 16.

## Architecture (CONFIRMED)

`BaseEarControlFragment` → earphone repository → `BluetoothService` → `HeadsetCoreService` → command encoder (`f7.a`/`f7.b`) → OPOv1 link framing (`v7.b`) → either Classic SPP (`p7.b`) or BLE GATT (`o7.h`).

The app supports both transports. Product type `101` selects the Classic/SPP client; `102` selects GATT. For the tested OnePlus Buds 4, the working command transport is **CONFIRMED** as Bluetooth Classic RFCOMM using UUID `0000079a-d102-11e1-9b23-00025b00a5a5`, SDP channel 15 / DLCI 30.

## BLE defaults (CONFIRMED as SDK defaults, not Buds 4-specific)

| Purpose | UUID |
|---|---|
| Service | `0000079a-d102-11e1-9b23-00025b00a5a5` |
| Write characteristic | `0100079a-d102-11e1-9b23-00025b00a5a5` |
| Notification characteristic | `0200079a-d102-11e1-9b23-00025b00a5a5` |
| CCCD | `00002902-0000-1000-8000-00805f9b34fb` |
| Standard battery service/characteristic referenced | `180f` / `2a19` |

## Inner command packet (CONFIRMED)

| Offset | Size | Meaning |
|---:|---:|---|
| 0 | 2 | command, unsigned little-endian |
| 2 | 1 | per-address sequence, wraps 0–255 |
| 3 | 2 | payload length, unsigned little-endian |
| 5 | N | payload |

## OPOv1 outer frame, unsliced small packet (CONFIRMED)

`AA LL 00 00 <inner packet>` where `LL = inner_length + 2` for the one-byte length form. Larger/sliced frames use variable-length length encoding and extra slice fields.

No checksum, CRC, encryption, or authentication is present in the traced gesture command/outer-frame code path. This does not prove their absence elsewhere in connection setup.

## OnePlus Buds 4 runtime transport (CONFIRMED)

| Property | Value |
|---|---|
| Transport | Bluetooth Classic RFCOMM |
| Command UUID | `0000079a-d102-11e1-9b23-00025b00a5a5` |
| RFCOMM channel | 15 |
| DLCI | 30 |
| Independent read request | `AA 07 00 00 08 01 00 00 00` |
| Independent read response | `0x8108`, 18 mapping records |

No additional handshake was required in successful independent runs. The first
connection attempt sometimes failed with Android `read ret: -1`; retrying after
the socket was released connected successfully without sending extra protocol data.

## Discovery/handshake

- Device discovery consults `SupportDeviceConfig` using product ID/device name.
- Classic path uses bonded `BluetoothDevice` and RFCOMM/SPP; its product UUID may be configured.
- BLE path connects GATT, discovers the configured/default service, enables notifications, and writes framed bytes to the write characteristic.
- Exact advertisement identifiers and product ID remain **UNKNOWN**. The transport and command UUID are runtime-confirmed as above.

## Current write-validation gate

The narrow first write test is complete. An independent client successfully:

1. Saved `02 01 02 06`.
2. Sent `0x0401` with `02 01 02 00`.
3. Received `0x8401` status `00`.
4. Read back `02 01 02 00` using `0x0108`.
5. Physically confirmed that Right double-tap was disabled.
6. Restored `02 01 02 06`, received status `00`, and verified it by read-back
   and physical behavior.

The `0x0401` set path, `0x8401` success response, and post-write `0x0108`
verification are therefore **CONFIRMED ON ONEPLUS BUDS 4** for this selector.
Bulk writes remain untested and must not yet be assumed atomic.
