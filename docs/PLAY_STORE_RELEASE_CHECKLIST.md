# Play Store Release Checklist

## Build/security
- [ ] `com.snaploop.app` registered in the existing Firebase project.
- [ ] Production `google-services.json` installed locally/CI secret injection; never committed.
- [ ] SHA-1/SHA-256 registered for dev/release signing as required by phone auth/App Check.
- [ ] Play Integrity App Check validated on Play-distributed build.
- [ ] AuraFace model exact SHA-256 verified at runtime.
- [ ] Release signing uses Play App Signing/upload key; no key/password in repository.
- [ ] R8 release build succeeds; mapping retained for Crashlytics.
- [ ] Cleartext disabled; exported components/deep links reviewed.
- [ ] Backup/data transfer does not include SnapLoop local sensitive state.

## Gates
- [ ] `gradle clean test lint assembleDebug assembleRelease`
- [ ] `connectedDebugAndroidTest` on representative API levels/devices.
- [ ] Firebase Emulator rules/functions positive + denied-access tests.
- [ ] iOS↔Android Face golden embedding/match-decision test.
- [ ] Cross-platform acceptance matrix complete except explicitly deferred originals.
- [ ] No known P0/P1; no production fake/stub paths.

## Play Console
- [ ] Data Safety answers reconciled to release code/vendor configs.
- [ ] Privacy policy/Face Match notice cover Android without conflicting with iOS.
- [ ] App access/reviewer instructions prepared for phone auth if needed.
- [ ] Content rating, target audience, permissions, screenshots/store listing complete.
- [ ] Internal testing track first; promote only after release gates pass.
