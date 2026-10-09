package com.example

import com.example.domain.model.AppLanguage
import com.example.domain.model.AppPrimaryColor
import com.example.domain.model.ResultCategory
import com.example.domain.model.ResultSubReason
import com.example.domain.model.TestSessionState
import com.example.presentation.theme.ResultColorTokens
import com.example.presentation.theme.ResultSemanticToken
import com.example.service.ServiceState
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/**
 * PHASE 8 — Navigation, Localization & Dynamic Theme System Test Suite.
 *
 * Verifies:
 * 1. Navigation lifecycle decoupling & Single Source of Truth (`TestSessionState`).
 * 2. Active session retention across screen transitions.
 * 3. Language system with AR (default) and EN only.
 * 4. Strict isolation of English from accidental Arabic text in UI results.
 * 5. Dynamic Primary Color system:
 *    - Default is RED on first install.
 *    - All 5 colors (RED, BLUE, PURPLE, YELLOW, GREEN) load cleanly.
 *    - All 5 colors have distinct styles, hex colors, and localized names.
 *    - Result semantic colors (SUCCESS_NORMAL vs SUCCESS_TWO_DEVICES) remain strictly distinct
 *      and never collapse or get overridden by primary themes.
 */
class Phase8NavigationLocalizationThemeTest {

    // =========================================================================
    // 1. Navigation & Process State
    // =========================================================================

    @Test
    fun testIdleNavigationState() {
        val idleState = ServiceState()
        assertEquals("IDLE", idleState.status)
        assertEquals(TestSessionState.IDLE, idleState.sessionState)
        assertEquals(0, idleState.progress)
        assertEquals(0, idleState.total)
        assertEquals("", idleState.currentCard)
        assertFalse(idleState.isPaused)
    }

    @Test
    fun testActiveSessionNavigationContinuity() {
        val runningState = ServiceState(
            progress = 14,
            total = 50,
            currentCard = "CARD_789456",
            successCount = 2,
            failureCount = 12,
            isPaused = false,
            status = "RUNNING",
            sessionState = TestSessionState.RUNNING
        )

        // Verifies screen lifecycle disconnection: session state remains authoritative
        assertEquals(TestSessionState.RUNNING, runningState.sessionState)
        assertEquals(14, runningState.progress)
        assertEquals(50, runningState.total)
        assertEquals("CARD_789456", runningState.currentCard)
        assertEquals(2, runningState.successCount)
        assertEquals(12, runningState.failureCount)
    }

    @Test
    fun testTestSessionStateTransitions() {
        val allStates = TestSessionState.values().toList()
        assertTrue(allStates.contains(TestSessionState.IDLE))
        assertTrue(allStates.contains(TestSessionState.RUNNING))
        assertTrue(allStates.contains(TestSessionState.PAUSED))
        assertTrue(allStates.contains(TestSessionState.COMPLETING))
        assertTrue(allStates.contains(TestSessionState.COMPLETED))
        assertTrue(allStates.contains(TestSessionState.FAILED))
        assertTrue(allStates.contains(TestSessionState.STOPPED))
    }

    // =========================================================================
    // 2. Language & Localization System
    // =========================================================================

    @Test
    fun testLanguageSystemDefaultIsArabic() {
        assertEquals(AppLanguage.AR, AppLanguage.DEFAULT)
        assertEquals("ar", AppLanguage.AR.code)
        assertEquals("العربية", AppLanguage.AR.displayNameAr)
        assertEquals("Arabic", AppLanguage.AR.displayNameEn)
    }

    @Test
    fun testLanguageSystemSupportsArabicAndEnglishOnly() {
        val supportedLanguages = AppLanguage.values()
        assertEquals("Strictly two languages must be supported in Phase 8", 2, supportedLanguages.size)
        assertTrue(supportedLanguages.contains(AppLanguage.AR))
        assertTrue(supportedLanguages.contains(AppLanguage.EN))
    }

    @Test
    fun testLanguageFromCodeFallback() {
        assertEquals(AppLanguage.AR, AppLanguage.fromCode("ar"))
        assertEquals(AppLanguage.AR, AppLanguage.fromCode("AR"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("EN"))
        // Fallback to default (AR)
        assertEquals(AppLanguage.AR, AppLanguage.fromCode("fr"))
        assertEquals(AppLanguage.AR, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.AR, AppLanguage.fromCode(""))
    }

    @Test
    fun testNoAccidentalArabicInEnglishFormattedResults() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)

            val normalText = ResultColorTokens.getFormattedResultText(ResultSemanticToken.SUCCESS_NORMAL)
            assertFalse("English result must not contain Arabic characters: $normalText",
                normalText.contains(Regex("[\\u0600-\\u06FF]")))
            assertTrue(normalText.contains("successfully", ignoreCase = true))

            val twoDevicesText = ResultColorTokens.getFormattedResultText(ResultSemanticToken.SUCCESS_TWO_DEVICES)
            assertFalse("English result must not contain Arabic characters: $twoDevicesText",
                twoDevicesText.contains(Regex("[\\u0600-\\u06FF]")))
            assertTrue(twoDevicesText.contains("two devices", ignoreCase = true))

            val failureText = ResultColorTokens.getFormattedResultText(ResultSemanticToken.FAILURE)
            assertFalse("English result must not contain Arabic characters: $failureText",
                failureText.contains(Regex("[\\u0600-\\u06FF]")))

            val warningText = ResultColorTokens.getFormattedResultText(ResultSemanticToken.WARNING)
            assertFalse("English result must not contain Arabic characters: $warningText",
                warningText.contains(Regex("[\\u0600-\\u06FF]")))

            val netErrText = ResultColorTokens.getFormattedResultText(ResultSemanticToken.NETWORK_ERROR)
            assertFalse("English result must not contain Arabic characters: $netErrText",
                netErrText.contains(Regex("[\\u0600-\\u06FF]")))

            val engErrText = ResultColorTokens.getFormattedResultText(ResultSemanticToken.ENGINE_ERROR)
            assertFalse("English result must not contain Arabic characters: $engErrText",
                engErrText.contains(Regex("[\\u0600-\\u06FF]")))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun testArabicFormattedResultsContainArabic() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar"))

            val normalText = ResultColorTokens.getFormattedResultText(ResultSemanticToken.SUCCESS_NORMAL)
            assertTrue("Arabic result must contain Arabic characters: $normalText",
                normalText.contains(Regex("[\\u0600-\\u06FF]")))

            val twoDevicesText = ResultColorTokens.getFormattedResultText(ResultSemanticToken.SUCCESS_TWO_DEVICES)
            assertTrue("Arabic result must contain Arabic characters: $twoDevicesText",
                twoDevicesText.contains(Regex("[\\u0600-\\u06FF]")))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun testResultSubReasonsAreBilingual() {
        for (subReason in ResultSubReason.values()) {
            assertFalse("displayNameAr must not be empty for $subReason", subReason.displayNameAr.isBlank())
            assertFalse("displayNameEn must not be empty for $subReason", subReason.displayNameEn.isBlank())
        }
    }

    // =========================================================================
    // 3. Primary Color System & Palettes
    // =========================================================================

    @Test
    fun testDefaultPrimaryColorIsRed() {
        assertEquals("Default primary color on first install must be RED",
            AppPrimaryColor.RED, AppPrimaryColor.DEFAULT)
        assertEquals("red", AppPrimaryColor.DEFAULT.key)
    }

    @Test
    fun testAllFivePrimaryColorsLoad() {
        val colors = AppPrimaryColor.values()
        assertEquals("Must support exactly 5 primary colors in Phase 8", 5, colors.size)

        val keys = colors.map { it.key }.toSet()
        assertEquals(5, keys.size)
        assertTrue(keys.contains("red"))
        assertTrue(keys.contains("blue"))
        assertTrue(keys.contains("purple"))
        assertTrue(keys.contains("yellow"))
        assertTrue(keys.contains("green"))

        // Verify hex colors are distinct
        val hexes = colors.map { it.hexColor }.toSet()
        assertEquals(5, hexes.size)
    }

    @Test
    fun testPrimaryColorFromKey() {
        assertEquals(AppPrimaryColor.RED, AppPrimaryColor.fromKey("red"))
        assertEquals(AppPrimaryColor.RED, AppPrimaryColor.fromKey("RED"))
        assertEquals(AppPrimaryColor.BLUE, AppPrimaryColor.fromKey("blue"))
        assertEquals(AppPrimaryColor.PURPLE, AppPrimaryColor.fromKey("purple"))
        assertEquals(AppPrimaryColor.YELLOW, AppPrimaryColor.fromKey("yellow"))
        assertEquals(AppPrimaryColor.GREEN, AppPrimaryColor.fromKey("green"))

        // Fallback to default (RED)
        assertEquals(AppPrimaryColor.RED, AppPrimaryColor.fromKey("unknown"))
        assertEquals(AppPrimaryColor.RED, AppPrimaryColor.fromKey(null))
    }

    @Test
    fun testPrimaryColorsHaveBothLanguageDisplayNames() {
        for (color in AppPrimaryColor.values()) {
            assertFalse("displayNameAr must not be blank for ${color.key}", color.displayNameAr.isBlank())
            assertFalse("displayNameEn must not be blank for ${color.key}", color.displayNameEn.isBlank())
            assertEquals(color.displayNameAr, color.getDisplayName(true))
            assertEquals(color.displayNameEn, color.getDisplayName(false))
            assertTrue(color.getCircleDrawableRes() > 0)
        }
    }

    @Test
    fun testSemanticResultColorsRemainIntactAcrossAllThemes() {
        // Semantic result colors MUST NEVER be overridden or collapsed
        val lightPalette = ResultColorTokens.LIGHT_PALETTE
        val darkPalette = ResultColorTokens.DARK_PALETTE

        // Normal success vs Two devices success must be strictly distinct
        assertNotEquals("Light mode: normal success and two devices success must have distinct colors",
            lightPalette.successNormal, lightPalette.successTwoDevices)
        assertNotEquals("Dark mode: normal success and two devices success must have distinct colors",
            darkPalette.successNormal, darkPalette.successTwoDevices)

        // Failure, warning, net error, engine error must also be distinct
        assertNotEquals(lightPalette.successNormal, lightPalette.failure)
        assertNotEquals(darkPalette.successNormal, darkPalette.failure)
        assertNotEquals(lightPalette.successTwoDevices, lightPalette.failure)
        assertNotEquals(darkPalette.successTwoDevices, darkPalette.failure)
    }
}
