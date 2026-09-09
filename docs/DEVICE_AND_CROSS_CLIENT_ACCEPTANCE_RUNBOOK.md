# Device and Cross-client Acceptance Runbook

Use this only after the repository source gate is green. Current source checkpoint: `c71b64e10ef51cddc306563330ad645cfcf8fc20`, Android CI run 347 / workflow run `34409879463`.

The purpose of this runbook is to convert the remaining release gates into observable pass/fail checks. Do not mark a row complete from code inspection alone.

## 0. Preconditions

- Android test device/emulator has a production-compatible `google-services.json` supplied locally (never commit it).
- Firebase Android app `com.snaploop.app` exists in the same project used by the pinned iOS build.
- Correct SHA-1/SHA-256 fingerprints are registered for the signing identity under test.
- For Play Integrity/App Check validation, use a Play-distributed build signed through the intended Play App Signing configuration.
- One Android device and one iPhone are available for cross-client scenarios.
- Use test accounts/data only. Do not capture or retain unnecessary biometric/photo evidence.

## 1. Local build and instrumentation gate

```bash
./gradlew clean test lint assembleDebug assembleRelease assembleDebugAndroidTest
```

Expected: all tasks succeed.

With an Android device/emulator attached:

```bash
adb devices
./gradlew connectedDebugAndroidTest
```

Expected: connected instrumentation tests succeed. Preserve only the test report/logs needed for release evidence.

## 2. Fresh install / onboarding / Firebase phone auth

1. Uninstall SnapLoop from Android.
2. Install the current Debug or internal-test build.
3. Launch SnapLoop.
4. Confirm onboarding appears before authentication.
5. Complete onboarding once.
6. Complete Firebase phone OTP.
7. Relaunch and confirm authenticated session restoration.
8. Relaunch again and confirm onboarding is not replayed.

Pass only if all eight observations succeed.

## 3. Legacy onboarding migration

Use a build/install state that contains the legacy per-user onboarding completion key, then upgrade to the current build without clearing app data.

Expected: the global `snaploop.onboarding.completed` behavior is satisfied without replaying onboarding after upgrade.

## 4. Create/Edit/Event concurrency

### Android create → iOS visibility
1. Create an Event on Android with name, location, type and civil dates.
2. Open the same Event on iOS.
3. Confirm fields, timezone/day semantics and lifecycle state agree.

### iOS create → Android visibility
Repeat in reverse.

### Concurrent edit protection
1. Open Edit Event on Android and leave it open.
2. Change the same Event from iOS/admin client and save.
3. Return to Android and attempt to save the stale edit.

Expected: Android blocks the save and instructs the user to reopen/review the latest Event; the newer server revision is not overwritten.

## 5. Join by code, link and QR

For each direction, test one valid Event and one invalid/expired input.

- iOS organizer → Android participant by Event code.
- iOS organizer → Android participant by invite link.
- iOS organizer QR → Android camera scanner.
- Android organizer → iOS participant by Event code/link/QR.

Expected: valid input resolves to the same Event and invitation review provenance; invalid input is rejected without joining.

## 6. Membership generation / leave / rejoin

1. Join Event on Android and record the logical membership generation from backend/debug evidence.
2. Leave Event.
3. Confirm old membership no longer receives/publishes matches.
4. Rejoin Event.
5. Confirm a fresh membership identity/generation is used.
6. Confirm stale generation remains revoked.

## 7. Face model and alignment golden tests

Use the same consented test images/profile inputs on both platforms.

Verify:
- 512-D model output contract.
- expected AuraFace model SHA-256 at Android runtime.
- aligned face crop/landmark geometry is comparable between iOS Vision and Android ML Kit.
- normalized embedding comparison remains within the agreed tolerance.
- same-person decisions agree.
- clearly different-person decisions agree/reject.
- ambiguity/corroboration policy agrees at threshold boundaries.

Do not use arbitrary production-user photos as golden fixtures.

## 8. Face Setup / Test My Face / replacement

On Android physical camera:

1. Complete five-template Face Setup with pose guidance.
2. Confirm saved setup survives relaunch.
3. Run Test My Face and confirm test selfie is not persisted.
4. Replace with the same person and confirm accepted revision propagation.
5. Attempt replacement with a different person and confirm rejection.
6. Confirm iOS observes the updated profile revision where applicable.

## 9. Consent and jurisdiction

Against the production-compatible backend:

- Canada outside Quebec: Face Match can be enabled with consent.
- India: Face Match can be enabled with consent.
- Quebec: Face Match remains disabled/rejected.
- Unsupported US launch path: server/client remains blocked according to deployed policy.
- Withdraw consent: matching stops and server-authoritative consent state changes.
- Delete Face Setup: Face Setup/template/reference are removed and matching stops, while biometric consent remains separately represented until explicitly withdrawn.

## 10. Media permission matrix

Run representative tests on API 26, 28, 29, 33 and 34+.

Verify:
- denied state;
- authorized/full state where supported;
- Android 14+ Selected Photos state;
- Add More Photos/manage access flow;
- SnapLoop scans only photos accessible to the app and only within Event date bounds.

## 11. Large-library / interruption / cancellation

Use a representative large local photo library.

Verify:
- Event-window enumeration remains bounded;
- progress is visible;
- Stop Scan cancels current work;
- interruption/background/foreground transitions do not cause unauthorized scanning;
- resumable encrypted scan state works as designed;
- no arbitrary raw photo upload occurs.

## 12. Match publish / Gallery / Favorites / Not Me

1. Produce a real consented match from one client to the other.
2. Verify recipient Gallery/Event My Photos updates.
3. Exercise grid density, full-screen paging/zoom, Save/Share, favorite/unfavorite and dedup behavior.
4. Exercise Not Me from Event and global Gallery scopes.
5. Verify authorization and membership/profile revision rules remain enforced.

## 13. Notifications

On Android 13+:

1. Grant/deny notification permission and confirm graceful behavior.
2. Send/trigger an Event invite push.
3. Send/trigger a matched-photo/update push.
4. Confirm lock-screen content is non-sensitive/private.
5. Tap notification and confirm routing/refresh target.
6. Confirm stale invite notification cleanup after resolution.

## 14. Privacy cleanup

### Face Setup deletion
Verify local encrypted reference cleanup plus expected backend profile/roster revocation.

### Consent withdrawal
Verify matching stops independently of Face Setup deletion.

### Account deletion
Verify server cleanup, sign-out/terminal navigation and absence of local biometric remnants.

## 15. Accessibility

With TalkBack enabled and large font/display scaling:

- Home/Event/Gallery/You primary navigation is reachable in logical order.
- You actionable rows are announced as coherent controls.
- Join Event heading, field, Continue and Scan QR Code controls are announced.
- Join validation errors are announced without requiring focus hunting.
- QR scanner announces purpose, close action, camera preview context and scanner errors.
- text remains usable without clipping/overlap on representative screens.
- contrast remains acceptable for text, enabled/disabled controls and status messaging.

## 16. Play-distributed release gate

Using the intended internal testing track:

- Install the Play-distributed build.
- Confirm Play Integrity-backed Firebase App Check succeeds.
- Confirm Firebase phone auth works with the registered release fingerprints.
- Confirm release-only R8 behavior has no missing-class/reflection/runtime regressions.
- Retain release mapping according to Crashlytics/Play release process.

## 17. Release decision

Release-passing requires:

- current branch CI green;
- connected instrumentation green on representative device/API coverage;
- Firebase/backend denied-access and positive-path checks green;
- cross-platform matrix green except intentionally unavailable original-photo transfer;
- Play Console/Data Safety/privacy/app-access/store metadata reconciled to the exact release build;
- no known P0/P1 defects.
