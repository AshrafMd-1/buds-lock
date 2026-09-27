# Gesture mapping representation

## Wire record (CONFIRMED)

Every mapping is exactly four bytes:

| Offset | DTO field | Meaning |
|---:|---|---|
| 0 | `deviceType` | target ear/target category |
| 1 | `deviceButton` | physical control selector |
| 2 | `buttonAction` | gesture/action selector |
| 3 | `function` | assigned behavior |

Controlled one-change experiments establish `deviceType 01 = left` and
`deviceType 02 = right` as **CONFIRMED**. Value `04 = both` remains a
**HYPOTHESIS** from the UI flow and has not been observed in the Buds 4 records.

## None (CONFIRMED)

Android resource `melody_ui_earphone_function_control_none` has ID `0x7f120537` and text `None`. `K8.a.a(context, selectedLabel, productId)` maps that resource to integer `0`. The DTO serializes it as one byte. Therefore:

`None` → `function = 0x00`.

## Runtime-confirmed function values

| Value | Meaning | Status/evidence |
|---:|---|---|
| `00` | None | **CONFIRMED** by APK resource mapping and controlled changes |
| `01` | Play/Pause | **CONFIRMED**: Right single-tap controlled change `01 → 00`, then restored |
| `05` | Previous track | **CONFIRMED**: Right triple-tap controlled change `05 → 00`, then restored |
| `06` | Next track | **CONFIRMED**: HeyMelody displayed Next track before both controlled double-tap changes |

Other values visible in the baseline are not promoted to confirmed labels solely
from a screenshot/configuration correlation; they require controlled one-change diffs.

## Product-defined selectors

`deviceButton` and `buttonAction` are derived from the control metadata supplied
for each settings row. The selector fields required for the initial Sleep Lock
scope are now confirmed:

| UI gesture | Left record | Right record | Status |
|---|---|---|---|
| Single-tap, not on a call | `01 01 01 FN` | `02 01 01 FN` | **CONFIRMED** (`BA=01`) by isolated Right single-tap change; `DT` side values independently confirmed |
| Double-tap, not on a call | `01 01 02 FN` | `02 01 02 FN` | **CONFIRMED** by isolated left and right changes |
| Triple-tap, not on a call | `01 01 03 FN` | `02 01 03 FN` | **CONFIRMED** (`BA=03`) by isolated Right triple-tap change; `DT` side values independently confirmed |

Selectors for slide, touch-and-hold, and call controls remain outside the v2.0
scope and must not be changed.

For the right-side record, independent writes establish the complete mapping:

- `02 01 02 06` = Right double-tap → Next track.
- `02 01 02 00` = Right double-tap → None.

Both directions were acknowledged, read back, and tested physically.

Additional controlled reads established:

- `02 01 01 01` = Right single-tap → Play/Pause.
- `02 01 01 00` = Right single-tap → None.
- `02 01 03 05` = Right triple-tap → Previous track.
- `02 01 03 00` = Right triple-tap → None.

## Sleep Lock implications

The v2.0 target is exactly six records: BA `01`, `02`, and `03` in DB `01`,
for DT `01` and `02`. Read the current records with `0x0108`, require all six
targets, retain the complete originals, and write those same selectors with
only `FN` changed to `00`. Persist all six originals before the first write.
Restore those exact values later. Never include swipe, hold, or call-control
records in this operation.
