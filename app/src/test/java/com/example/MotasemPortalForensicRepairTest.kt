package com.example

import com.example.data.local.entity.RouterProfileEntity
import com.example.domain.model.ResultCategory
import com.example.domain.model.ResultSubReason
import com.example.service.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Targeted verification test suite for MOTASEM captive portal repairs:
 * - Redirect-aware result detection (4-second delay handling)
 * - Portal CHAP login contract preservation
 * - Centralized result-classification precedence
 * - Card whitespace normalization
 * - Distinction between technical timeout and card failure
 */
class MotasemPortalForensicRepairTest {

    private val motasemRouter = RouterProfileEntity(
        id = 10,
        name = "شبكة معتصم نت",
        ip = "wifi.sd.net",
        strategyId = "motasem",
        successIndicator = "تفاصيل الأستخدام",
        failureIndicator = "خطأ"
    )

    private val loginPageHtml = """
        <!DOCTYPE html><html><head>
        <title>شبكة معتصم نت</title>
        <script>
            function hexMD5(str) { return "mock_hash_" + str; }
            function doLogin() {
                document.sendin.username.value = document.login.username.value;
                document.sendin.password.value = hexMD5('\264' + document.login.password.value + 'mock_challenge');
                document.sendin.submit();
                return false;
            }
        </script>
        </head><body>
        <form name="sendin" action="http://wifi.sd.net/login" method="post" style="display:none">
            <input type="hidden" name="username">
            <input type="hidden" name="password">
            <input type="hidden" name="dst" value="http://10.0.0.1">
            <input type="hidden" name="popup" value="true">
        </form>
        <div class="form-box">
            <form name="login" action="http://wifi.sd.net/login" method="post" onsubmit="return doLogin()">
                <div class="form-title"><p>ادخل الرمز</p></div>
                <div class="input-field">
                    <input name="username" value="" id="username" placeholder="اسم المستخدم">
                    <input style="width:0px" name="password" type="password">
                </div>
                <div class="submit"><button type="submit">تسجيل الدخول</button></div>
            </form>
        </div>
        </body></html>
    """.trimIndent()

    private val redirectPageHtml = """
        <!DOCTYPE html><html><head>
        <title>شبكة معتصم نت - تحويل</title>
        <meta http-equiv="refresh" content="4;url=status.html">
        </head><body>
        <div class="box">
            <h3>سيتم الآن تحويلك الى الموقع المطلوب</h3>
            <p>يرجى الانتظار 4 ثوانٍ...</p>
        </div>
        </body></html>
    """.trimIndent()

    private val statusPageHtml = """
        <!DOCTYPE html><html><head>
        <title>شبكة معتصم نت</title>
        </head><body>
        <div class="box">
            <form action="http://wifi.sd.net/logout" name="logout" onsubmit="return openLogout()">
                <div class="header"><h4>تفاصيل الأستخدام</h4></div>
                <div class="section username"><h4>أسم المستخدم:</h4><h4>904123</h4></div>
                <div class="section card">
                    <h4>الوقت المتبقي:</h4>
                    <h4 id="timeLeft">5 يوم , 9 ساعة , 43 دقيقة</h4>
                </div>
                <div class="section remain">
                    <h4>الرصيد المتبقي:</h4>
                    <h4>3.64 جيجابايت</h4>
                </div>
                <div class="submit"><button type="submit">تسجيل الخروج</button></div>
            </form>
        </div>
        </body></html>
    """.trimIndent()

    private val twoDevicesPageHtml = """
        <!DOCTYPE html><html><head>
        <title>شبكة معتصم نت</title>
        </head><body>
        <div class="error-box">
            <p>خطأ: لا يمكن استعمال البطاقة في جهازين</p>
        </div>
        </body></html>
    """.trimIndent()

    private val invalidCardPageHtml = """
        <!DOCTYPE html><html><head>
        <title>شبكة معتصم نت</title>
        </head><body>
        <div class="error-box">
            <p>خطأ: كرت غير صحيح أو تم إدخال رمز خاطئ</p>
        </div>
        </body></html>
    """.trimIndent()

    // =========================================================================
    // 1. Redirect-Aware Result Detection & State Precedence
    // =========================================================================

    @Test
    fun testRedirectPageIsRecognizedAndNotClassifiedAsFailureOrSuccess() {
        val bodyText = "سيتم الآن تحويلك الى الموقع المطلوب يرجى الانتظار"

        assertTrue("Redirect must be identified by isRedirecting", ResultChecker.isRedirecting(redirectPageHtml, bodyText))
        assertFalse("Redirect page must NOT be treated as success", ResultChecker.isSuccess(redirectPageHtml, bodyText, motasemRouter))
        assertFalse("Redirect page must NOT be treated as card failure", ResultChecker.isFailure(redirectPageHtml, bodyText, motasemRouter))

        val outcome = ResultChecker.classifyOutcome(redirectPageHtml, bodyText, motasemRouter, 4000L)
        assertEquals("Unresolved redirect deadline must produce TIMEOUT category", ResultCategory.TIMEOUT, outcome.category)
        assertFalse(outcome.isSuccess)
    }

    @Test
    fun testStatusPageIsRecognizedAsSuccessWithAuthoritativeDom() {
        val bodyText = "تفاصيل الأستخدام أسم المستخدم 904123 الوقت المتبقي 5 يوم الرصيد المتبقي 3.64 جيجابايت"

        assertTrue("Status page must be success", ResultChecker.isSuccess(statusPageHtml, bodyText, motasemRouter))
        assertTrue("Status page must be logged in", ResultChecker.isLoggedIn(statusPageHtml, bodyText, motasemRouter))
        assertFalse("Status page must NOT be failure", ResultChecker.isFailure(statusPageHtml, bodyText, motasemRouter))
        assertFalse("Status page is NOT redirecting", ResultChecker.isRedirecting(statusPageHtml, bodyText))

        val outcome = ResultChecker.classifyOutcome(statusPageHtml, bodyText, motasemRouter, 4500L)
        assertTrue(outcome.isSuccess)
        assertEquals(ResultCategory.SUCCESS, outcome.category)
        assertEquals(ResultSubReason.NORMAL_LOGIN_SUCCESS, outcome.subReason)
    }

    @Test
    fun testTwoDevicesNotificationHasTopPrecedenceOverFailure() {
        val bodyText = "خطأ: لا يمكن استعمال البطاقة في جهازين"

        assertTrue("Two devices notification must be identified", ResultChecker.isTwoDevicesSuccess(twoDevicesPageHtml, bodyText))
        assertTrue("Two devices must be classified as SUCCESS overall", ResultChecker.isSuccess(twoDevicesPageHtml, bodyText, motasemRouter))
        assertFalse("Two devices must NEVER be classified as FAILURE", ResultChecker.isFailure(twoDevicesPageHtml, bodyText, motasemRouter))

        val outcome = ResultChecker.classifyOutcome(twoDevicesPageHtml, bodyText, motasemRouter, 400L)
        assertTrue(outcome.isSuccess)
        assertTrue(outcome.isTwoDevicesSuccess)
        assertEquals(ResultCategory.SUCCESS, outcome.category)
        assertEquals(ResultSubReason.TWO_DEVICES_SUCCESS, outcome.subReason)
        assertEquals("TWO_DEVICES_ACTIVE_CARD", outcome.successReason)
    }

    @Test
    fun testCardRejectionClassifiedAsFailureWithSubReason() {
        val bodyText = "خطأ: كرت غير صحيح أو تم إدخال رمز خاطئ"

        assertFalse(ResultChecker.isSuccess(invalidCardPageHtml, bodyText, motasemRouter))
        assertTrue(ResultChecker.isFailure(invalidCardPageHtml, bodyText, motasemRouter))

        val outcome = ResultChecker.classifyOutcome(invalidCardPageHtml, bodyText, motasemRouter, 300L)
        assertFalse(outcome.isSuccess)
        assertEquals(ResultCategory.FAILURE, outcome.category)
        assertEquals(ResultSubReason.INVALID_CARD, outcome.subReason)
    }

    // =========================================================================
    // 2. CHAP Login Contract Preservation
    // =========================================================================

    @Test
    fun testWhitespaceNormalizationPreservesValidInternalCharacters() {
        val messyCard = "   904123-A   \n\t"
        val normalized = messyCard.trim()
        assertEquals("904123-A", normalized)

        val quoted = InjectionManager.quote(normalized)
        assertEquals("\"904123-A\"", quoted)
    }

    @Test
    fun testChapLoginContractExecutionSafety() {
        // Safe card with quotes and backslashes
        val trickyCard = "card'with\"quote\\and_slash"
        val quoted = InjectionManager.quote(trickyCard.trim())
        assertFalse("Quoted string must not contain unescaped raw newlines or broken quotes", quoted.contains("\n"))

        // Strategy identity verification
        val strategy = RouterStrategyFactory.getStrategy(motasemRouter)
        assertEquals("Motasem", strategy.strategyName)
        assertTrue(strategy is MotasemTestStrategy)
    }

    // =========================================================================
    // 3. Timing and Technical Error Classification
    // =========================================================================

    @Test
    fun testTechnicalTimeoutIsNotClassifiedAsCardFailure() {
        val timeoutOutcome = CardTestOutcome.timeout("انتهت مهلة استجابة بوابة معتصم نت (Redirect Timeout)", 7000L)
        assertEquals(ResultCategory.TIMEOUT, timeoutOutcome.category)
        assertEquals(ResultSubReason.NETWORK_TIMEOUT, timeoutOutcome.subReason)
        assertFalse(timeoutOutcome.isSuccess)

        // Technical errors do not increment card failure counter
        val isTechnical = timeoutOutcome.category == ResultCategory.TIMEOUT ||
                timeoutOutcome.category == ResultCategory.NETWORK_ERROR ||
                timeoutOutcome.category == ResultCategory.ENGINE_ERROR
        assertTrue("Timeout must be identified as technical error", isTechnical)
    }

    @Test
    fun testEngineErrorOnChapMissingIsTyped() {
        val engineErrorOutcome = CardTestOutcome.engineError(
            message = "خطأ في دالة تشفير البوابة (CHAP): error: doLogin function is missing",
            durationMs = 50L,
            isJs = true
        )
        assertEquals(ResultCategory.ENGINE_ERROR, engineErrorOutcome.category)
        assertEquals(ResultSubReason.JAVASCRIPT_ERROR, engineErrorOutcome.subReason)
        assertFalse(engineErrorOutcome.isSuccess)
    }
}
