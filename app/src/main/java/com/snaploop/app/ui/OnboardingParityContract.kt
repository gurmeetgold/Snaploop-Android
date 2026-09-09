package com.snaploop.app.ui

/** Platform-neutral copy/state contract mirrored from pinned iOS OnboardingView.swift. */
internal enum class OnboardingParityKind { FIND, FACE, TRIP, RESULT, PRIVACY }

internal data class OnboardingParityPage(
    val kind: OnboardingParityKind,
    val title: String,
    val body: String,
    val primaryCta: String,
    val note: String,
)

internal object OnboardingParityContract {
    val pages: List<OnboardingParityPage> = listOf(
        OnboardingParityPage(
            kind = OnboardingParityKind.FIND,
            title = "Find every photo you're in",
            body = "After an Event (e.g. trip), your best photos may be sitting on everyone else's phones. SnapLoop automatically finds the photos you're in and brings them to your phone.",
            primaryCta = "See How It Works",
            note = "No more asking everyone to send you their photos.",
        ),
        OnboardingParityPage(
            kind = OnboardingParityKind.FACE,
            title = "Set up your face once",
            body = "Take a quick selfie scan so SnapLoop can recognize you in Event photos. Your selfie and reference images stay only on this device and are not uploaded to SnapLoop.",
            primaryCta = "Continue",
            note = "To enable matching, SnapLoop stores only face-template metadata - not your selfie photo.",
        ),
        OnboardingParityPage(
            kind = OnboardingParityKind.TRIP,
            title = "Create an Event or join one",
            body = "Create an Event (e.g. trip, party or family gathering) for your group, or join a friend's Event with an invite. Everyone chooses whether to participate, and each Event has its own people and date range.",
            primaryCta = "Continue",
            note = "Nobody is added silently — each person chooses to join.",
        ),
        OnboardingParityPage(
            kind = OnboardingParityKind.RESULT,
            title = "Your photos come to you",
            body = "SnapLoop finds photos of you from participating Event members' phones and shares those matches with you automatically. Photos where you are not matched are not shared with you.",
            primaryCta = "Continue",
            note = "Save the photos you like to your own photo library.",
        ),
        OnboardingParityPage(
            kind = OnboardingParityKind.PRIVACY,
            title = "Private by design",
            body = "SnapLoop never uploads your entire photo library. It checks only photos within your Event's selected date range, and face matching happens on your device.",
            primaryCta = "Start Using SnapLoop",
            note = "All Event-related cloud data, including matched photo previews, is deleted within 15 days after the Event ends.",
        ),
    )
}
