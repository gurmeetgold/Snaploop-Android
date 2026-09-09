# iOS ↔ Android Parity Matrix

Source-of-truth baseline: iOS `chatgpt/release-canada-gallery-name-limits-2026-09-03` at `2627881fce5f9b736f0a717e808f0ea2e811ac98`. README history is non-authoritative.

Android implementation checkpoint: `46fe58aa3d1bbbb1ce3ec1784d6d5fcc3d7e756c`, verified by Android CI run 219 (`test`, `lint`, `assembleDebug`, `assembleDebugAndroidTest`).

Status legend:
- ✅ implementation/contract complete for the audited scope and covered by current branch CI/unit/static verification.
- 🟡 required emulator/device/cross-client acceptance is still pending even when implementation is present.
- ⬜ implementation is missing or has not yet been ported/audited.
- ⏭ intentionally deferred because the pinned iOS release also does not expose the capability.

| Capability | iOS source/contract | Android implementation | Backend/privacy constraint | Remaining verification | Status |
|---|---|---|---|---|---|
| App shell Home/Gallery/You | current SwiftUI RootView/screens | Compose main shell with parity Home/Event/Gallery/You surfaces and Android back semantics | no backend change | visual/device walkthrough against pinned screenshots | ✅ impl · 🟡 acceptance |
| Phone OTP auth | Firebase Auth | Firebase Auth Android, country-code/verification/session restoration flow | same project; no OTP logging | real-device Firebase phone auth | ✅ impl · 🟡 acceptance |
| Event create/edit | Event + FirebaseEventRepository + functions | civil-date Create/Edit UI, exact managed payloads/status lifecycle | stable event ID/token; server authority | emulator/real backend cross-client event mutation | ✅ impl · 🟡 acceptance |
| Join code/link/QR | pinned Events invitation flow | App Link/custom URI parser, manual code/token, camera QR, review/decline provenance | reject untrusted/expired inputs | iOS↔Android device link/QR matrix | ✅ impl · 🟡 acceptance |
| Membership generation | EventMember/functions | server-issued membership IDs carried through face roster/match context | stale generations must never receive matches | leave/rejoin emulator + cross-client test | ✅ impl · 🟡 acceptance |
| Event civil dates | eventDateSemantics.js | `LocalDate` + explicit Event timezone/day-number contract | no midnight timezone drift | deterministic DST/unit coverage | ✅ |
| Remote Config | RemoteConfigValues.swift | Firebase Remote Config with fail-safe defaults and observable Event grace | no permissive biometric fallback | unit/static integration | ✅ |
| Face model | AuraFace v5 Core ML | same v5 identity contract via `glintr100.onnx` + ONNX Runtime; runtime SHA-256 verification | 512-D; model never downloaded dynamically | iOS↔Android golden embedding/device test | ✅ impl · 🟡 acceptance |
| Face alignment/preprocessing | pinned `Services/FaceEngine/FaceAligner.swift` + Core ML conversion contract | five-point 112×112 ArcFace geometry; BGR CHW `(pixel-127.5)/128`; L2 output normalization | all processing on-device | detector-specific Vision↔ML Kit golden crop/embedding comparison | ✅ impl · 🟡 acceptance |
| Matching policy | FaceModelPolicy + matcher corroboration/strong-single/ambiguity | same v5.2 thresholds and multi-template decision policy | precision first | unit contract coverage; benchmark remains release gate | ✅ |
| Face Setup | guided five-template enrollment | guided CameraX capture, pose tracker, five-template save, local encrypted face-reference crop | selfies local; profile templates use server contract | physical-device pose/camera + cross-client profile test | ✅ impl · 🟡 acceptance |
| Face Test / replacement | pinned Face Setup identity-update behavior | Face Test camera + same-person replacement policy and tests | different-person replacement rejected | device camera + iOS↔Android replacement test | ✅ impl · 🟡 acceptance |
| Consent v5 | biometric consent model/UI/server contract | disclosure/attestation UI + server-authoritative consent store | no matching without active consent | emulator/server integration | ✅ impl · 🟡 acceptance |
| Jurisdiction | launch/server policy | CA/IN policy, Quebec Face Match exclusion, tested normalization | server remains authoritative | end-to-end server rejection/acceptance matrix | ✅ impl · 🟡 acceptance |
| Media access | PhotoKit | MediaStore/scoped media + denied/limited/authorized Android 14 state handling | own accessible library only | API 26/28/29/33/34+ physical/emulator permission matrix | ✅ impl · 🟡 acceptance |
| Event-date scan | pinned scanner/automatic sync | bounded Event-window MediaStore enumeration, resumable encrypted scan state, cancellation and foreground auto-scan policy | never upload arbitrary photos; lifecycle/sharing gates | large-library/device/background-interruption run | ✅ impl · 🟡 acceptance |
| Match publish | pinned match/function schema | Firebase match publication with source-installation/membership/profile-revision metadata, merge/removal semantics and bounded preview | membership/profile revision + idempotency | emulator + iOS↔Android recipient test | ✅ impl · 🟡 acceptance |
| Gallery/favorites/Not Me | pinned Event My Photos + global Gallery | 2/3/4/6 grid, favorites, bulk Save/Share, full-screen paging/zoom, scoped dedup, Event-vs-global Not Me semantics | recipient authorization; temporary share cache only | screenshot/device UX and real matched-thumbnail exercise | ✅ impl · 🟡 acceptance |
| Original-photo transfer | pinned release unavailable | intentionally unavailable in Android v1 | no invented Android-only transfer protocol | verify unavailable state | ⏭ |
| Sharing pause/leave/rejoin | member/photo-preference/functions | sharing controls, leave/manage-member flows, lifecycle-aware scan suppression | revoke stale membership generation | cross-client stale-generation/rejoin acceptance | ✅ impl · 🟡 acceptance |
| Notifications | APNs/notification semantics | Firebase Messaging dependency exists, but no audited Android FCM service/channel/private-lock-screen implementation yet | no sensitive lock-screen copy | implement service/channel/token handling, then device tests | ⬜ |
| Withdraw/delete Face Setup | pinned privacy flow/functions | Face Setup deletion/withdrawal parity actions + local reference cleanup | immediate stop/revocation | emulator/server cleanup + device UX | ✅ impl · 🟡 acceptance |
| Account deletion | pinned server cleanup | Privacy surface + account deletion coordinator/server flow | equivalent erasure; no local biometric remnants | emulator/server cleanup verification | ✅ impl · 🟡 acceptance |
| Analytics/Crash/Perf | separately validated analytics branch | Firebase observability dependencies; product analytics remains separately reviewed | no photos/embeddings/PII/session replay | merge only after privacy allowlist review/tests | 🟡 |
| Backup security | iOS protected local state | backup disabled/excluded; face model/reference/scan state kept in app-private/no-backup or encrypted storage | no biometric/tokens/URIs in backup | manifest/static inspection | ✅ |
| Accessibility | SwiftUI semantics | partial Compose content descriptions/touch targets exist, but no complete audited accessibility pass | no product redesign | TalkBack, font scaling, contrast, focus-order instrumentation/device pass | ⬜ |
