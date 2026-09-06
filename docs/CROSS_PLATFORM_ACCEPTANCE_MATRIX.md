# Cross-platform Acceptance Matrix

A row is release-passing only when observed against the same production-compatible Firebase contract; contract reasoning alone is not marked as device-verified.

| Scenario | Required result | Current status |
|---|---|---|
| iOS organizer → Android participant | create/invite/join/Face Setup/scan/match/Gallery | pending device integration |
| Android organizer → iOS participant | same reversed | pending device integration |
| Android Face Profile → iOS scanner | equivalent identity decision | pending golden runtime test |
| iOS Face Profile → Android scanner | equivalent identity decision | pending golden runtime test |
| iOS QR → Android | same Event | parser foundation done; camera/device pending |
| Android QR → iOS | same Event | pending |
| Android leave → old generation | receives/publishes nothing | pending emulator/cross-client |
| Android rejoin | fresh membership identity | pending emulator/cross-client |
| Consent withdrawal | matching stops on both clients | pending function/device |
| Face Setup deletion | profile/roster revocation equivalent | pending |
| Account deletion | server cleanup equivalent | pending |
| Event expiry/grace | same join/sync/download semantics | pending |
| Original request | current iOS-release unavailable behavior | intentionally same for v1; production originals next shared release |
