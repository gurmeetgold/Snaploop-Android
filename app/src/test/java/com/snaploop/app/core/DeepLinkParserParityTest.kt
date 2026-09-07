package com.snaploop.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkParserParityTest {
    @Test
    fun joinCodeNormalizationMatchesIosContract() {
        assertEquals("AB2KM9", DeepLinkParser.normalizeCode("ab2-km9"))
        assertEquals("AB2-KM9", DeepLinkParser.formatCode("ab2km9"))
        assertNull(DeepLinkParser.normalizeCode("AB0KM9"))
        assertNull(DeepLinkParser.normalizeCode("AB1KM9"))
        assertNull(DeepLinkParser.normalizeCode("ABOKM9"))
        assertNull(DeepLinkParser.normalizeCode("ABIKM9"))
        assertNull(DeepLinkParser.normalizeCode("ABLKM9"))
        assertNull(DeepLinkParser.normalizeCode("TOO-LONG"))
    }

    @Test
    fun inviteTokenRequiresExactlyTwentyTwoAlphaNumericCharacters() {
        val token = "AbCdEfGhJkLmNpQrStUvWx"
        assertEquals(token, DeepLinkParser.normalizeToken(token))
        assertNull(DeepLinkParser.normalizeToken(token.dropLast(1)))
        assertNull(DeepLinkParser.normalizeToken(token + "Y"))
        assertNull(DeepLinkParser.normalizeToken("AbCdEfGhJkLmNpQrStUvW-"))
    }

    @Test
    fun generatedInviteUrlAndShareCopyUseCanonicalEventRoute() {
        val token = "AbCdEfGhJkLmNpQrStUvWx"
        val url = DeepLinkParser.inviteUrl(token, "getsnaploop.web.app")
        assertEquals("https://getsnaploop.web.app/e/$token", url)
        val copy = DeepLinkParser.shareText("Banff Weekend", "Gurmeet", token)
        assertTrue(copy.startsWith("Gurmeet invited you to join \"Banff Weekend\" on SnapLoop:"))
        assertTrue(copy.endsWith(url))
    }
}
