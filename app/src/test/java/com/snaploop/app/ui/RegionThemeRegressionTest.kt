package com.snaploop.app.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionThemeRegressionTest {
    @Test
    fun `consent offers global residence list and device-aware default`() {
        val source = Files.readString(Paths.get("src/main/java/com/snaploop/app/ui/ParityConsentScreen.kt"))
        assertTrue(source.contains("Locale.getISOCountries()"))
        assertTrue(source.contains("ParityPhoneNumberSupport.deviceRegionCode(context)"))
        assertTrue(source.contains("Face Match is not available for the selected residence yet."))
        assertFalse(source.contains("Text(if (country == \"CA\") \"Canada\" else \"India\")"))
    }

    @Test
    fun `production theme remains light until dark palette is complete`() {
        val source = Files.readString(Paths.get("src/main/java/com/snaploop/app/ui/SnapLoopTheme.kt"))
        assertTrue(source.contains("val colors = lightColorScheme("))
        assertFalse(source.contains("isSystemInDarkTheme()"))
    }
}
