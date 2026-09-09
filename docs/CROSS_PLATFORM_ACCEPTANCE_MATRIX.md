# Cross-platform Acceptance Matrix

Source/CI implementation checkpoint: `c71b64e10ef51cddc306563330ad645cfcf8fc20`, green in Android CI run 347 / workflow run `34409879463`.

A row is release-passing only when the behavior is actually observed against the same production-compatible Firebase contract. Source parity, unit tests, compiled instrumentation tests and successful Release/R8 assembly are prerequisites, not substitutes for device/cross-client observation.

| Scenario | Required result | Source/CI readiness | Remaining acceptance |
|---|---|---|---|
| Fresh install → onboarding → Android auth | onboarding appears before auth once; Firebase OTP/session restoration works | ready | physical Android + Firebase |
| Legacy Android install upgrade | legacy per-user onboarding completion migrates without replaying onboarding | ready | upgraded install/device |
| iOS organizer → Android participant | create/invite/join/Face Setup/scan/match/Gallery | ready | iOS + Android + Firebase |
| Android organizer → iOS participant | same reversed | ready | Android + iOS + Firebase |
| iOS manual code/link → Android | same Event, correct invitation review | ready | cross-client device |
| iOS QR → Android | same Event via on-device camera QR scan | ready | camera/device |
| Android QR → iOS | same Event | ready | cross-client device |
| Concurrent Event edit | stale Android editor never silently overwrites newer Event revision | ready; pre-save revision guard covered | two clients + backend |
| Android Face Profile → iOS scanner | equivalent identity decision | ready | golden runtime/device test |
| iOS Face Profile → Android scanner | equivalent identity decision | ready | golden runtime/device test |
| Vision ↔ ML Kit alignment | equivalent crop/embedding decision within agreed tolerance | ready | shared golden face set/device |
| Face Setup / Test My Face | five captures, pose guidance, local encrypted reference, test flow | ready | physical camera/device |
| Same-person Face replacement | accepted and revision propagated | ready | cross-client/device |
| Different-person Face replacement | rejected | ready | physical/device |
| Android leave → old generation | old membership receives/publishes nothing | ready | emulator/cross-client |
| Android rejoin | fresh membership identity; old generation remains revoked | ready | emulator/cross-client |
| Consent withdrawal | matching stops on both clients/server authority | ready | function/device |
| Quebec / unsupported jurisdiction | Face Match remains unavailable/rejected according to server policy | ready | server/device jurisdiction matrix |
| Face Setup deletion | local reference/profile/roster revocation equivalent; consent remains separately controlled | ready | server/device cleanup |
| Account deletion | server cleanup equivalent and no local biometric remnants | ready | server/device cleanup |
| Event expiry/grace | same join/sync/download semantics | ready | backend clock/lifecycle test |
| API 26/28/29 media access | platform-appropriate access and bounded Event scan | ready | emulator/physical matrix |
| API 33 media access | Android 13 media permission behavior | ready | emulator/physical matrix |
| API 34+ Selected Photos | Selected Photos state, Add More Photos/manage access, bounded scan | ready | Android 14+ device/emulator |
| Large photo library | bounded Event-window scan, progress, cancellation, resumable state | ready | representative large library/device |
| Background/interruption | no unauthorized background behavior; scan resumes/cancels safely | ready | physical lifecycle test |
| Match publish/recipient | membership/profile revision respected; recipient Gallery updates correctly | ready | cross-client Firebase |
| Favorites/Not Me | Event/global semantics and matched thumbnail behavior match pinned iOS | ready | matched-photo device UX |
| Android 13+ notifications | permission, invite/photo delivery, private lock-screen copy and routing work | ready | FCM/device |
| Accessibility | headings/action rows/errors/scanner semantics exposed; Join AndroidTest compiles | ready | TalkBack, large font, contrast, focus order on device |
| Original-photo transfer | current pinned iOS release behavior remains unavailable | intentionally same for v1 | no further v1 acceptance beyond confirming unavailable state |
