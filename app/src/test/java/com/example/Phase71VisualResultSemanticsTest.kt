package com.example

import com.example.data.local.entity.RouterProfileEntity
import com.example.data.local.entity.TestResultEntity
import com.example.domain.model.ResultCategory
import com.example.domain.model.ResultSubReason
import com.example.domain.model.TestResult
import com.example.presentation.theme.ResultColorTokens
import com.example.presentation.theme.ResultSemanticToken
import com.example.service.CardTestOutcome
import com.example.service.ResultChecker
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * PHASE 7.1 — Visual Result Semantics Addendum Test Suite.
 *
 * Verifies:
 * 1. Normal SUCCESS receives `successNormal`.
 * 2. TWO_DEVICES_SUCCESS receives `successTwoDevices`.
 * 3. The two tokens are never equal.
 * 4. Theme changes do not collapse the two semantic colors into one.
 * 5. Light and Dark mode both preserve the distinction.
 * 6. Semantic distinctness of all tokens from each other and from the brand primary theme color.
 * 7. Authoritative model resolution (Category + SubReason), not arbitrary text.
 * 8. Specific captive portal phrase "لا يمكن استعمال البطاقة في جهازين" maps to SUCCESS_TWO_DEVICES.
 * 9. Formatted display text distinguishes meanings:
 *    - "✓ تم تسجيل البطاقة بنجاح"
 *    - "✓ البطاقة صالحة لكنها مستخدمة على جهاز آخر"
 * 10. Export JSON serialization preserves subReason and successReason.
 */
class Phase71VisualResultSemanticsTest {

    // =========================================================================
    // 1. Token Assignment: Normal SUCCESS vs TWO_DEVICES_SUCCESS
    // =========================================================================

    @Test
    fun testNormalSuccessReceivesSuccessNormalToken() {
        val normalToken = ResultColorTokens.resolveToken(
            ResultCategory.SUCCESS,
            ResultSubReason.NORMAL_LOGIN_SUCCESS
        )
        assertEquals(ResultSemanticToken.SUCCESS_NORMAL, normalToken)
        assertEquals("successNormal", normalToken.tokenName)

        val outcome = CardTestOutcome.success("دخول سليم", 500L)
        assertEquals(ResultSemanticToken.SUCCESS_NORMAL, ResultColorTokens.resolveToken(outcome))

        val entity = TestResultEntity(
            sessionId = 1L,
            cardCode = "CARD001",
            routerId = 1L,
            routerName = "MikroTik",
            state = "Success",
            message = "تم الدخول بنجاح",
            durationMs = 500L
        )
        assertEquals(ResultSemanticToken.SUCCESS_NORMAL, ResultColorTokens.resolveToken(entity))
    }

    @Test
    fun testTwoDevicesSuccessReceivesSuccessTwoDevicesToken() {
        val twoDevicesToken = ResultColorTokens.resolveToken(
            ResultCategory.SUCCESS,
            ResultSubReason.TWO_DEVICES_SUCCESS
        )
        assertEquals(ResultSemanticToken.SUCCESS_TWO_DEVICES, twoDevicesToken)
        assertEquals("successTwoDevices", twoDevicesToken.tokenName)

        val outcome = CardTestOutcome.twoDevicesSuccess()
        assertEquals(ResultSemanticToken.SUCCESS_TWO_DEVICES, ResultColorTokens.resolveToken(outcome))

        val entity = TestResultEntity(
            sessionId = 1L,
            cardCode = "CARD002",
            routerId = 1L,
            routerName = "MikroTik",
            state = "Success",
            message = "تم التحقق: لا يمكن استعمال البطاقة في جهازين (البطاقة صالحة)",
            durationMs = 600L
        )
        assertEquals(ResultSemanticToken.SUCCESS_TWO_DEVICES, ResultColorTokens.resolveToken(entity))
    }

    // =========================================================================
    // 2. Token Inequality Guarantee
    // =========================================================================

    @Test
    fun testNormalAndTwoDevicesTokensAreNeverEqual() {
        assertNotEquals(
            "successNormal and successTwoDevices tokens must NEVER be equal",
            ResultSemanticToken.SUCCESS_NORMAL,
            ResultSemanticToken.SUCCESS_TWO_DEVICES
        )
        assertNotEquals(
            ResultSemanticToken.SUCCESS_NORMAL.tokenName,
            ResultSemanticToken.SUCCESS_TWO_DEVICES.tokenName
        )
    }

    // =========================================================================
    // 3. Light Mode Preserves Distinction
    // =========================================================================

    @Test
    fun testLightModePreservesDistinction() {
        val lightPalette = ResultColorTokens.LIGHT_PALETTE

        val normalSuccessColor = lightPalette.getColor(ResultSemanticToken.SUCCESS_NORMAL)
        val twoDevicesColor = lightPalette.getColor(ResultSemanticToken.SUCCESS_TWO_DEVICES)
        val failureColor = lightPalette.getColor(ResultSemanticToken.FAILURE)
        val warningColor = lightPalette.getColor(ResultSemanticToken.WARNING)
        val networkErrorColor = lightPalette.getColor(ResultSemanticToken.NETWORK_ERROR)
        val engineErrorColor = lightPalette.getColor(ResultSemanticToken.ENGINE_ERROR)
        val primaryColor = lightPalette.primaryTheme

        // 1. Two devices is not equal to normal success (Amber != Green)
        assertNotEquals("Light mode: successTwoDevices must not equal successNormal", normalSuccessColor, twoDevicesColor)

        // 2. Two devices is distinct from failure (Amber != Red)
        assertNotEquals("Light mode: successTwoDevices must not equal failure", failureColor, twoDevicesColor)

        // 3. Two devices is distinct from warning (Amber != Yellow)
        assertNotEquals("Light mode: successTwoDevices must not equal warning", warningColor, twoDevicesColor)

        // 4. Two devices is distinct from network error (Amber != Blue)
        assertNotEquals("Light mode: successTwoDevices must not equal networkError", networkErrorColor, twoDevicesColor)

        // 5. Two devices is distinct from engine error (Amber != Magenta)
        assertNotEquals("Light mode: successTwoDevices must not equal engineError", engineErrorColor, twoDevicesColor)

        // 6. Two devices is distinct from primary brand theme (Amber != Purple)
        assertNotEquals("Light mode: successTwoDevices must not equal primaryTheme", primaryColor, twoDevicesColor)
    }

    // =========================================================================
    // 4. Dark Mode Preserves Distinction
    // =========================================================================

    @Test
    fun testDarkModePreservesDistinction() {
        val darkPalette = ResultColorTokens.DARK_PALETTE

        val normalSuccessColor = darkPalette.getColor(ResultSemanticToken.SUCCESS_NORMAL)
        val twoDevicesColor = darkPalette.getColor(ResultSemanticToken.SUCCESS_TWO_DEVICES)
        val failureColor = darkPalette.getColor(ResultSemanticToken.FAILURE)
        val warningColor = darkPalette.getColor(ResultSemanticToken.WARNING)
        val networkErrorColor = darkPalette.getColor(ResultSemanticToken.NETWORK_ERROR)
        val engineErrorColor = darkPalette.getColor(ResultSemanticToken.ENGINE_ERROR)
        val primaryColor = darkPalette.primaryTheme

        // 1. Two devices is not equal to normal success (Amber != Green)
        assertNotEquals("Dark mode: successTwoDevices must not equal successNormal", normalSuccessColor, twoDevicesColor)

        // 2. Two devices is distinct from failure (Amber != Red)
        assertNotEquals("Dark mode: successTwoDevices must not equal failure", failureColor, twoDevicesColor)

        // 3. Two devices is distinct from warning (Amber != Yellow)
        assertNotEquals("Dark mode: successTwoDevices must not equal warning", warningColor, twoDevicesColor)

        // 4. Two devices is distinct from network error (Amber != Blue)
        assertNotEquals("Dark mode: successTwoDevices must not equal networkError", networkErrorColor, twoDevicesColor)

        // 5. Two devices is distinct from engine error (Amber != Rose)
        assertNotEquals("Dark mode: successTwoDevices must not equal engineError", engineErrorColor, twoDevicesColor)

        // 6. Two devices is distinct from primary brand theme (Amber != Purple)
        assertNotEquals("Dark mode: successTwoDevices must not equal primaryTheme", primaryColor, twoDevicesColor)
    }

    // =========================================================================
    // 5. Theme Changes Do Not Collapse Semantic Colors
    // =========================================================================

    @Test
    fun testThemeChangesDoNotCollapseColors() {
        val lightPalette = ResultColorTokens.LIGHT_PALETTE
        val darkPalette = ResultColorTokens.DARK_PALETTE

        // In neither theme do normal and two-devices collapse into each other
        assertNotEquals(lightPalette.successNormal, lightPalette.successTwoDevices)
        assertNotEquals(darkPalette.successNormal, darkPalette.successTwoDevices)

        // Cross-theme check: normal success in light is distinct from two-devices in dark and vice-versa
        assertNotEquals(lightPalette.successNormal, darkPalette.successTwoDevices)
        assertNotEquals(darkPalette.successNormal, lightPalette.successTwoDevices)

        // Verification via getColor API
        assertEquals(lightPalette.successNormal, ResultColorTokens.getColor(ResultSemanticToken.SUCCESS_NORMAL, isDarkMode = false))
        assertEquals(darkPalette.successNormal, ResultColorTokens.getColor(ResultSemanticToken.SUCCESS_NORMAL, isDarkMode = true))
        assertEquals(lightPalette.successTwoDevices, ResultColorTokens.getColor(ResultSemanticToken.SUCCESS_TWO_DEVICES, isDarkMode = false))
        assertEquals(darkPalette.successTwoDevices, ResultColorTokens.getColor(ResultSemanticToken.SUCCESS_TWO_DEVICES, isDarkMode = true))
    }

    // =========================================================================
    // 6. Portal Phrase "لا يمكن استعمال البطاقة في جهازين" End-To-End Resolution
    // =========================================================================

    @Test
    fun testPortalTwoDevicesNotificationResolvesToAmberVisualToken() {
        val router = RouterProfileEntity(name = "AlBasha Router", ip = "192.168.1.1")
        val portalHtml = """
            <html>
                <body>
                    <div class="alert-box">
                        <span>لا يمكن استعمال البطاقة في جهازين</span>
                    </div>
                </body>
            </html>
        """.trimIndent()
        val portalText = "لا يمكن استعمال البطاقة في جهازين"

        val outcome = ResultChecker.classifyOutcome(portalHtml, portalText, router, 750L)

        // Authoritative classification remains SUCCESS
        assertTrue("Must remain SUCCESS", outcome.isSuccess)
        assertEquals(ResultCategory.SUCCESS, outcome.category)
        assertEquals(ResultSubReason.TWO_DEVICES_SUCCESS, outcome.subReason)
        assertEquals("TWO_DEVICES_ACTIVE_CARD", outcome.successReason)

        // Visual Semantic Token receives SUCCESS_TWO_DEVICES (Amber / Orange)
        val token = ResultColorTokens.resolveToken(outcome)
        assertEquals(ResultSemanticToken.SUCCESS_TWO_DEVICES, token)

        // Color in Light mode is Amber/Orange (#FFE65100)
        assertEquals(0xFFE65100.toInt(), ResultColorTokens.getColor(token, isDarkMode = false))
        // Color in Dark mode is Vibrant Amber (#FFFFB74D)
        assertEquals(0xFFFFB74D.toInt(), ResultColorTokens.getColor(token, isDarkMode = true))
    }

    // =========================================================================
    // 7. Human-Readable Formatted Message Presentation
    // =========================================================================

    @Test
    fun testHumanReadableFormattedResultTextDistinguishesMeanings() {
        val normalText = ResultColorTokens.getFormattedResultText(
            ResultSemanticToken.SUCCESS_NORMAL,
            "تم تسجيل البطاقة بنجاح"
        )
        val twoDevicesText = ResultColorTokens.getFormattedResultText(
            ResultSemanticToken.SUCCESS_TWO_DEVICES,
            "لا يمكن استعمال البطاقة في جهازين"
        )
        val failureText = ResultColorTokens.getFormattedResultText(
            ResultSemanticToken.FAILURE,
            "بطاقة منتهية الصلاحية"
        )

        assertEquals("✓ تم تسجيل البطاقة بنجاح", normalText)
        assertEquals("✓ البطاقة صالحة لكنها مستخدمة على جهاز آخر", twoDevicesText)
        assertTrue(failureText.startsWith("✗"))

        // Ensure normal success and two devices success text are clearly distinct
        assertNotEquals(normalText, twoDevicesText)
    }

    private val json = Json { prettyPrint = true }

    // =========================================================================
    // 8. Result Model Serialization & Export Integrity
    // =========================================================================

    @Test
    fun testExportJsonDistinguishesNormalAndTwoDevicesSuccess() {
        val normalResult = TestResult(
            id = 1L,
            sessionId = 100L,
            cardCode = "998877",
            routerId = 1L,
            routerName = "Router1",
            state = "Success",
            message = "تم تسجيل الدخول",
            durationMs = 450L,
            subReason = "NORMAL_LOGIN_SUCCESS",
            successReason = "NORMAL_LOGIN"
        )

        val twoDevicesResult = TestResult(
            id = 2L,
            sessionId = 100L,
            cardCode = "112233",
            routerId = 1L,
            routerName = "Router1",
            state = "Success",
            message = "لا يمكن استعمال البطاقة في جهازين",
            durationMs = 550L,
            subReason = "TWO_DEVICES_SUCCESS",
            successReason = "TWO_DEVICES_ACTIVE_CARD"
        )

        val jsonString = json.encodeToString(listOf(normalResult, twoDevicesResult))

        assertTrue(jsonString.contains("\"subReason\": \"NORMAL_LOGIN_SUCCESS\""))
        assertTrue(jsonString.contains("\"successReason\": \"NORMAL_LOGIN\""))
        assertTrue(jsonString.contains("\"subReason\": \"TWO_DEVICES_SUCCESS\""))
        assertTrue(jsonString.contains("\"successReason\": \"TWO_DEVICES_ACTIVE_CARD\""))
    }
}
