# Changelog

## 2.0

- Declares the physically validated Sleep Lock implementation as the stable
  release milestone.
- Blocks and restores left/right single-, double-, and triple-tap mappings.
- Keeps swipe, touch-and-hold, call controls, ANC, and unrelated settings
  outside the mutation scope.
- Uses an icon-only transparent widget: accent when blocked, gray when normal
  or disconnected, and amber when restoration is required.
- Removes the decorative in-app icon background for normal and disconnected
  states while retaining the accented blocked presentation.
- Retains RFCOMM as the operation authority so OEM Bluetooth presentation
  false negatives cannot silently bypass the verified transaction.
- Includes the signed production APK, public signing certificate, and public
  checksums in `dist/`.

The Bluetooth commands, selectors, durable six-record recovery backup, and
write/read-back safety rules are unchanged from the validated 1.0.4 build.

