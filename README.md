# SnapLoop Android

Production Android client for SnapLoop, developed independently from iOS while preserving the shared Firebase/backend, biometric, privacy and Event contracts.

Active implementation branch: `feature/android-production-parity-v1`.

## Local prerequisites
- JDK 17
- Android SDK API 37 / Build Tools 36+
- Android Studio compatible with AGP 9.4
- `app/google-services.json` from the existing SnapLoop Firebase project (never commit it)
- verified AuraFace `glintr100.onnx` at `app/src/main/assets/models/glintr100.onnx` (never commit it)

The Android client intentionally matches the current iOS release behavior for original-photo retrieval; full cross-platform original transfer is deferred to a later shared-backend release.
