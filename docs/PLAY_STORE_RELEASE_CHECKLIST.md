# Play Store Release Checklist

Verified source/CI checkpoint: `c71b64e10ef51cddc306563330ad645cfcf8fc20`, Android CI run 347 / workflow run `34409879463`.

## Build/security
- [ ] `com.snaploop.app` registered in the existing Firebase project.
- [ ] Production `google-services.json` installed locally/CI secret injection; never committed.
- [ ] SHA-1/SHA-256 registered for dev/release signing as required by phone auth/App Check.
- [ ] Play Integrity App Check validated on Play-distributed build.
- [x] AuraFace model runtime includes exact SHA-256 verification; physical release-build verification remains in device acceptance.
- [ ] Release signing uses Play App Signing/upload key; no key/password in repository.
- [x] Minified/R8 release build succeeds in mandatory CI (`assembleRelease`); retain the production mapping artifact/configuration when Play signing/release distribution is enabled.
- [x] Cleartext disabled; exported components/deep links statically reviewed.
- [x] Backup/data transfer excludes SnapLoop local sensitive state.

## Automated repository gates
- [x] CI: `gradle test lint assembleDebug assembleRelease assembleDebugAndroidTest` — run 347 passed.
- [x] Unit/static parity and privacy/security regression tests pass in run 347.
- [x] AndroidTest APK, including Join accessibility smoke coverage, compiles in run 347.

## Environment/device gates
- [ ] `connectedDebugAndroidTest` on representative API levels/devices.
- [ ] Firebase Emulator rules/functions positive + denied-access tests.
- [ ] iOS↔Android Face golden embedding/match-decision test.
- [ ] Cross-platform acceptance matrix complete except explicitly deferred originals.
- [ ] Real-device Firebase phone OTP/session restoration.
- [ ] Android 13+ FCM permission/delivery/routing acceptance.
- [ ] API 26/28/29/33/34+ media-access behavior, including Android 14 Selected Photos.
- [ ] Physical TalkBack, large-font/font-scaling, contrast and focus-order walkthrough.
- [ ] No known P0/P1 after device/cross-client acceptance; no production fake/stub paths.

## Play Console
- [ ] Data Safety answers reconciled to release code/vendor configs.
- [ ] Privacy policy/Face Match notice cover Android without conflicting with iOS.
- [ ] App access/reviewer instructions prepared for phone auth if needed.
- [ ] Content rating, target audience, permissions, screenshots/store listing complete.
- [ ] Internal testing track first; promote only after release gates pass.
