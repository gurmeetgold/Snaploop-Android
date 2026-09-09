package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingParityContractTest {
    @Test
    fun `onboarding has the same five semantic pages as pinned iOS`() {
        assertEquals(
            listOf(
                OnboardingParityKind.FIND,
                OnboardingParityKind.FACE,
                OnboardingParityKind.TRIP,
                OnboardingParityKind.RESULT,
                OnboardingParityKind.PRIVACY,
            ),
            OnboardingParityContract.pages.map { it.kind },
        )
    }

    @Test
    fun `onboarding ctas match pinned iOS sequence`() {
        assertEquals(
            listOf(
                "See How It Works",
                "Continue",
                "Continue",
                "Continue",
                "Start Using SnapLoop",
            ),
            OnboardingParityContract.pages.map { it.primaryCta },
        )
    }

    @Test
    fun `android keeps platform neutral wording without changing privacy meaning`() {
        val face = OnboardingParityContract.pages.single { it.kind == OnboardingParityKind.FACE }
        assertTrue(face.body.contains("this device"))
        assertFalse(face.body.contains("iPhone"))
        assertTrue(face.note.contains("face-template metadata"))
        assertTrue(face.note.contains("not your selfie photo"))

        val privacy = OnboardingParityContract.pages.single { it.kind == OnboardingParityKind.PRIVACY }
        assertTrue(privacy.body.contains("never uploads your entire photo library"))
        assertTrue(privacy.body.contains("selected date range"))
        assertTrue(privacy.body.contains("on your device"))
        assertTrue(privacy.note.contains("within 15 days after the Event ends"))
    }

    @Test
    fun `event participation promises remain explicit`() {
        val trip = OnboardingParityContract.pages.single { it.kind == OnboardingParityKind.TRIP }
        assertTrue(trip.body.contains("Everyone chooses whether to participate"))
        assertTrue(trip.note.contains("Nobody is added silently"))
    }
}
