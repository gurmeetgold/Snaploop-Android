package com.snaploop.app.core

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepLinkParserInstrumentedTest {
    private val token = "AbCdEfGhJkLmNpQrStUvWx"

    @Test
    fun productionIosWebInviteParsesOnAndroid() {
        val parsed = DeepLinkParser.parse(Uri.parse("https://getsnaploop.web.app/e/$token"))
        assertNotNull(parsed)
        assertEquals(token, parsed?.token)
        assertEquals(InviteAction.REVIEW, parsed?.action)
    }

    @Test
    fun legacyDevWebInviteStillParsesOnAndroid() {
        val parsed = DeepLinkParser.parse(Uri.parse("https://snaploop-dev.web.app/e/$token"))
        assertNotNull(parsed)
        assertEquals(token, parsed?.token)
    }

    @Test
    fun customSchemeAndCodeRoutesRemainCompatible() {
        assertEquals(
            token,
            DeepLinkParser.parse(Uri.parse("snaploop://e/$token"))?.token,
        )
        assertEquals(
            "AB2KM9",
            DeepLinkParser.parse(Uri.parse("snaploop://c/AB2-KM9"))?.code,
        )
        assertEquals(
            token,
            DeepLinkParser.parse(Uri.parse("snaploop://join?token=$token"))?.token,
        )
    }

    @Test
    fun arbitraryHttpsHostIsRejected() {
        assertNull(DeepLinkParser.parse(Uri.parse("https://example.com/e/$token")))
    }
}
