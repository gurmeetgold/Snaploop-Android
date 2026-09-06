# Google Play Data Safety — implementation mapping

This is engineering evidence for the Play Console form; final answers must be reconciled against the release build and vendor configuration.

## On-device only
- Accessible MediaStore photos are enumerated only for the selected Event date range.
- Raw enrollment selfies/reference images remain device-local by product design.
- Face detection, alignment and identity matching run on-device.
- Local content URIs and scan bookkeeping must not be written to Firestore/analytics/logs.

## Transmitted to SnapLoop/Firebase
- Phone authentication identifier/verification metadata through Firebase Auth.
- Account/profile/Event/member records required for the service.
- Numerical Face Profile templates/embeddings and associated v5 identity/revision/consent metadata. Do **not** claim embeddings are exclusively on-device.
- Matched-photo preview/metadata only according to the existing iOS publishing contract; not the full photo library.
- FCM registration token and necessary Event notification routing metadata.

## Diagnostics/observability
- Crashlytics: crashes, ANRs and app/device diagnostics. No photos, embeddings, phone numbers, invite secrets, signed URLs or biometric scores may be attached.
- Firebase Performance: app/network performance diagnostics; custom trace names/attributes must be allowlisted and non-identifying.
- PostHog: only if the separately validated iOS analytics architecture is approved for Android release. No autocapture, session replay, image capture, biometric values or PII.

## User controls/retention
Face Match is optional and server-authoritative consent/withdrawal applies. Face Setup deletion and account deletion must invoke the same backend cleanup semantics as iOS. Event-related cloud retention follows the deployed backend/current customer-facing notice rather than Android-local assumptions.
