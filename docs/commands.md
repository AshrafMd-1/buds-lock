# Command catalogue

## Set gesture mapping

- Status: **CONFIRMED** statically; right-double-tap Buds 4 selector independently confirmed by controlled read diffs.
- Direction: Host → buds
- Command: decimal `1025`, hex `0x0401`
- Inner command bytes: `01 04 SS 05 00 01 DT DB BA FN` for one mapping.
- Small unsliced outer frame: `AA 0C 00 00 01 04 SS 05 00 01 DT DB BA FN`.

| Symbol | Meaning | Evidence/status |
|---|---|---|
| `SS` | sequence byte | **CONFIRMED** |
| `01` (payload byte 0) | record count | **CONFIRMED** |
| `DT` | `deviceType` | **CONFIRMED**: `01` left, `02` right |
| `DB` | `deviceButton` | **CONFIRMED**: `01` for the tested not-on-call tap records |
| `BA` | `buttonAction` | **CONFIRMED**: `01` single, `02` double, `03` triple tap |
| `FN` | assigned function | **CONFIRMED**; None is `00` |

For N records the payload is `count` followed by N four-byte records: `[deviceType, deviceButton, buttonAction, function]`.

### Example: one selected gesture → None

Right double-tap → None:

`AA 0C 00 00 01 04 SS 05 00 01 02 01 02 00`

The first three selector bytes are confirmed by two controlled experiments:
left double-tap changed only record `01 01 02`, and right double-tap changed only
record `02 01 02`. In both cases `Next track (06) → None (00)` changed only `FN`.

### Device-confirmed right double-tap transaction

The independent v0.4 probe sent:

`AA 0C 00 00 01 04 01 05 00 01 02 01 02 00`

The Buds replied:

`AA 08 00 00 01 84 01 01 00 00`

Decoded acknowledgement: command `0x8401`, sequence `01`, payload/status `00`.
The following `0x0108` read returned `02 01 02 00`. Physical testing confirmed
that Right double-tap no longer triggered Next track.

Restoration sent:

`AA 0C 00 00 01 04 01 05 00 01 02 01 02 06`

The Buds again returned `0x8401` status `00`; read-back returned
`02 01 02 06`, and physical testing confirmed the original behavior was restored.

Status: **CONFIRMED ON DEVICE**, including write, acknowledgement, read-back,
persistence across a new RFCOMM connection, restoration, and physical behavior.

Controlled read-only diffs additionally confirm Right single-tap as
`02 01 01 FN` and Right triple-tap as `02 01 03 FN`. Bulk Sleep Lock sends one
confirmed one-record command at a time and requires a final `0x0108`
verification; it does not assume that a multi-record write is accepted.

## Read/refresh gesture mappings

- Status: **CONFIRMED** command invocation and response record layout.
- Command: decimal `264`, hex `0x0108`.
- HeyMelody sends it immediately after the set operation to refresh/confirm state.
- Parsed response data: byte 0 **UNKNOWN**, byte 1 record count, then four-byte `KeyFunctionInfo` records starting at byte 2.

## Acknowledgement semantics

- Response command: `0x8401` (**CONFIRMED from APK parser**).
- Response payload byte 0 is the set status.
- Status `0x00` invokes the APK success callback; nonzero invokes failure.
- If present, response payload byte 1 is exposed as `mRspValue`; its meaning is **UNKNOWN**.

An independent client must still issue `0x0108` and compare the returned record;
an acknowledgement alone is not sufficient for Sleep Lock state.

## Other requested commands

ANC, transparency, ANC-off, battery/status, and device-information commands were not needed to establish the gesture write path and are **UNKNOWN / NOT YET TRACED** in this pass. They must not be guessed from adjacent command IDs.
