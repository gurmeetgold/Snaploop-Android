# iOS ↔ Android Parity Matrix

Source-of-truth baseline: iOS `chatgpt/release-canada-gallery-name-limits-2026-09-03` at `2627881fce5f9b736f0a717e808f0ea2e811ac98`. README history is non-authoritative.

Status legend: ✅ implemented/contract ported; 🟡 in progress; ⬜ pending; ⏭ intentionally deferred with iOS parity.

| Capability | iOS source/contract | Android target | Backend/privacy constraint | Verification | Status |
|---|---|---|---|---|---|
| App shell Home/Gallery/You | current SwiftUI RootView/screens | Compose, Android back semantics | no backend change | Compose UI | 🟡 |
| Phone OTP auth | Firebase Auth | Firebase Auth Android | same project; no OTP logging | emulator/device auth tests | 🟡 |
| Event create/edit | Event + FirebaseEventRepository + functions | exact DTO/callables | stable event ID/token; server authority | contract + emulator | 🟡 |
| Join code/link/QR | current Events flow | validated App Link/custom URI/QR | reject untrusted/expired inputs | parser/UI/device | 🟡 |
| Membership generation | EventMember/functions | server-issued membershipId | stale generations must never receive matches | rules/contract | 🟡 |
| Event civil dates | eventDateSemantics.js | LocalDate + explicit zone | no midnight timezone drift | deterministic DST tests | ✅ core |
| Remote Config | RemoteConfigValues.swift | Firebase Remote Config | fail-safe defaults, no permissive biometric fallback | unit/integration | ✅ core |
| Face model | AuraFace v5 | same glintr100.onnx via ONNX Runtime | 512-D; verified model SHA | golden iOS↔Android | 🟡 |
| Face alignment | canonical 5-point 112x112 | Android canonical alignment | identical preprocessing | golden crop/embedding | ⬜ |
| Matching policy | FaceMatcher.swift | exact corroboration/strong-single/ambiguity | precision first | unit/golden | ✅ core |
| Face Setup | 3–5/target 5 templates | guided Android capture | selfies local; profile templates server contract | UI/device/golden | ⬜ |
| Face Test / replacement | iOS Face Setup | same identity rule | different-person replacement rejected | unit/UI | ⬜ |
| Consent v5 | biometricConsentV5.js + UI | same callable/disclosure/attestations | server authoritative | emulator/UI | 🟡 |
| Jurisdiction | server policy | declared residence + server validation | CA supported except QC; IN; server authoritative | function tests | 🟡 |
| Media access | PhotoKit | MediaStore/scoped media | own accessible library only | API-level matrix | ⬜ |
| Event-date scan | scanner | bounded/cancellable scan | never upload arbitrary photos | unit/device | ⬜ |
| Match publish | server function contract | same callable/schema | membership/profile revision + idempotency | emulator/cross-client | ⬜ |
| Gallery/favorites/Not Me | current MyPhotos | Compose + local favorites | recipient authorization | UI/emulator | ⬜ |
| Original-photo transfer | current release unavailable | same current-release behavior | no invented Android-only protocol | verify unavailable state | ⏭ |
| Sharing pause/leave/rejoin | members/functions | same lifecycle | revoke stale membership generation | cross-platform | ⬜ |
| Notifications | FCM/APNs backend semantics | FCM channels + private lock screen | no sensitive lock-screen copy | device tests | ⬜ |
| Withdraw/delete Face Setup | current privacy flow/functions | same operations | immediate stop/revocation | emulator/UI | ⬜ |
| Account deletion | current server cleanup | same callable | equivalent erasure | emulator/UI | ⬜ |
| Analytics/Crash/Perf | separate validated analytics branch review | privacy allowlist + Firebase observability | no photos/embeddings/PII/session replay | allowlist tests | 🟡 |
| Backup security | iOS protected local state | Android backup disabled/excluded | no biometric/tokens/URIs in backup | manifest inspection | ✅ |
| Accessibility | SwiftUI semantics | Compose semantics/touch/font scaling | no product redesign | instrumentation | ⬜ |
