# Release signing

Production releases for package `dev.ashraf.buds4readonly` must use the same
private signing key for every update. The private keystore and its passwords
are deliberately stored outside this repository.

## Current production certificate

- Key alias: `budslock`
- Certificate SHA-256:
  `C9:37:7E:23:70:CE:24:D7:A8:3C:1B:7A:F1:54:A0:3F:34:49:53:56:52:F9:21:22:17:FE:6A:0D:88:17:9A:78`

The separately supplied signing backup contains the private PKCS#12 keystore
and credentials. Keep at least two encrypted/offline copies. Never commit that
backup, the keystore, or passwords to GitHub, including a private repository.

## Build a signed APK

Set the four environment variables documented in `README.md`, then run:

```bash
./verify-protocol.sh
./build-release.sh
```

Verify that the resulting APK reports the certificate fingerprint above before
installing or publishing it. An APK signed by a different key cannot update an
existing installation without uninstalling it and losing the app's local data.

