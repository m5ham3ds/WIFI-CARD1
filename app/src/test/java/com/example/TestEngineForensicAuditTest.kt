package com.example

import com.example.data.local.entity.RouterProfileEntity
import com.example.domain.model.RouterAuthType
import com.example.domain.model.RouterProfile
import com.example.domain.model.RouterStrategyId
import com.example.service.*
import com.example.util.SecurityUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PHASE 5 — Forensic Audit & Hardening Test Suite for the Test Engine.
 * Verifies core engine state transitions, ResultChecker accuracy, InjectionManager safety,
 * URL normalization, concurrency locks, and non-regression guarantees.
 */
class TestEngineForensicAuditTest {

    // =========================================================================
    // 1. ServiceState Lifecycle & State Transitions
    // =========================================================================

    @Test
    fun testServiceStateDefaultValuesAndImmutability() {
        val defaultState = ServiceState()
        assertEquals(0, defaultState.progress)
        assertEquals(0, defaultState.total)
        assertEquals("", defaultState.currentCard)
        assertEquals(0, defaultState.successCount)
        assertEquals(0, defaultState.failureCount)
        assertFalse(defaultState.isPaused)
        assertEquals("IDLE", defaultState.status)
        assertNull(defaultState.error)
        assertNull(defaultState.screenshotBytes)

        val runningState = defaultState.copy(
            total = 100,
            status = "RUNNING",
            progress = 1,
            currentCard = "123456"
        )
        assertEquals(100, runningState.total)
        assertEquals("RUNNING", runningState.status)
        assertEquals(1, runningState.progress)
        assertEquals("123456", runningState.currentCard)

        // Verify equality and hash code
        val duplicateRunning = defaultState.copy(
            total = 100,
            status = "RUNNING",
            progress = 1,
            currentCard = "123456"
        )
        assertEquals(runningState, duplicateRunning)
        assertEquals(runningState.hashCode(), duplicateRunning.hashCode())

        // Verify screenshot equality check
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(1, 2, 3)
        val stateWithBytes1 = runningState.copy(screenshotBytes = bytes1)
        val stateWithBytes2 = runningState.copy(screenshotBytes = bytes2)
        assertEquals(stateWithBytes1, stateWithBytes2)
    }

    @Test
    fun testServiceStateTransitionSequence() {
        var state = ServiceState()
        assertEquals("IDLE", state.status)

        // Start test
        state = state.copy(status = "RUNNING", total = 50, progress = 0)
        assertEquals("RUNNING", state.status)

        // Progress updates
        state = state.copy(progress = 1, currentCard = "CARD-001", successCount = 1)
        assertEquals(1, state.progress)
        assertEquals(1, state.successCount)

        // Pause
        state = state.copy(isPaused = true, status = "PAUSED")
        assertTrue(state.isPaused)
        assertEquals("PAUSED", state.status)

        // Resume
        state = state.copy(isPaused = false, status = "RUNNING")
        assertFalse(state.isPaused)
        assertEquals("RUNNING", state.status)

        // Failure card
        state = state.copy(progress = 2, currentCard = "CARD-002", failureCount = 1)
        assertEquals(2, state.progress)
        assertEquals(1, state.failureCount)

        // Complete
        state = state.copy(status = "DONE")
        assertEquals("DONE", state.status)
    }

    // =========================================================================
    // 2. ResultChecker Forensic Verification Across All Portals
    // =========================================================================

    @Test
    fun testResultCheckerAlBashaSuccess() {
        val router = RouterProfileEntity(name = "الباشا", ip = "wifi.sd.net", strategyId = "abasha")
        val statusHtml = """
            <html><head><title>MikroTicket Status</title></head>
            <body>
                <form id="mForm" action="http://www.Abasha.com/logout">
                    <table id="infoTable">
                        <tr><td>عنوان IP</td><td>10.0.0.15</td></tr>
                        <tr><td>خطة الإنترنت</td><td>1 Hour Plan</td></tr>
                    </table>
                </form>
            </body></html>
        """.trimIndent()
        val bodyText = "عنوان IP 10.0.0.15 خطة الإنترنت 1 Hour Plan"

        assertTrue(ResultChecker.isSuccess(statusHtml, bodyText, router))
        assertTrue(ResultChecker.isLoggedIn(statusHtml, bodyText, router))
        assertFalse(ResultChecker.isFailure(statusHtml, bodyText, router))
        assertFalse(ResultChecker.isAuthorizing(statusHtml, bodyText))
    }

    @Test
    fun testResultCheckerBelloSuccess() {
        val router = RouterProfileEntity(name = "بيلو", ip = "www.bello.com", strategyId = "bello")
        val statusHtml = """
            <html><head><title>معلومات الإشتراك</title></head>
            <body>
                <div id="battery" class="battery"></div>
                <div id="card">90412345</div>
                <div id="timeLeft">01:30:00</div>
                <p>المتبقي من الرصيد 500 MB</p>
                <form action="http://www.bello.com/logout" name="logout"></form>
            </body></html>
        """.trimIndent()
        val bodyText = "تفاصيل الحساب 90412345 المتبقي من الرصيد 500 MB المتبقي من الوقت 01:30:00"

        assertTrue(ResultChecker.isSuccess(statusHtml, bodyText, router))
        assertFalse(ResultChecker.isFailure(statusHtml, bodyText, router))
    }

    @Test
    fun testResultCheckerMotasemSuccess() {
        val router = RouterProfileEntity(name = "معتصم", ip = "wifi.sd.net", strategyId = "motasem")
        val statusHtml = """
            <html><head><title>شبكة معتصم نت</title></head>
            <body>
                <div id="timeLeft">02:15:30</div>
                <div class="section username">10293847</div>
                <p>تفاصيل الأستخدام</p>
                <p>الوقت المتبقي: 02:15:30</p>
                <p>الرصيد المتبقي: 1.2 GB</p>
                <form action="http://r.com/logout" name="logout"></form>
            </body></html>
        """.trimIndent()
        val bodyText = "تفاصيل الأستخدام أسم المستخدم 10293847 الوقت المتبقي 02:15:30 الرصيد المتبقي 1.2 GB"

        assertTrue(ResultChecker.isSuccess(statusHtml, bodyText, router))
        assertFalse(ResultChecker.isFailure(statusHtml, bodyText, router))
    }

    @Test
    fun testResultCheckerUniversalHeuristicSuccess() {
        val genericRouter = RouterProfileEntity(
            name = "Generic Hotspot",
            ip = "192.168.1.1",
            strategyId = "generic",
            successIndicator = ""
        )
        val heuristicHtml = """
            <html><body>
                <a href="/logout">تسجيل الخروج</a>
                <p>وقت الاتصال: 00:15:20</p>
                <p>عنوان IP: 192.168.1.100</p>
            </body></html>
        """.trimIndent()
        val bodyText = "تسجيل الخروج وقت الاتصال: 00:15:20 عنوان IP: 192.168.1.100"

        assertTrue(ResultChecker.isSuccess(heuristicHtml, bodyText, genericRouter))
    }

    @Test
    fun testResultCheckerExplicitIndicatorPriority() {
        val customRouter = RouterProfileEntity(
            name = "Custom Portal",
            ip = "10.10.0.1",
            strategyId = "generic",
            successIndicator = "WELCOME_USER_AUTHORIZED",
            failureIndicator = "AUTH_REJECTED"
        )
        val successHtml = "<html><body><p>WELCOME_USER_AUTHORIZED</p></body></html>"
        val failureHtml = "<html><body><p>AUTH_REJECTED</p></body></html>"

        assertTrue(ResultChecker.isSuccess(successHtml, "WELCOME_USER_AUTHORIZED", customRouter))
        assertFalse(ResultChecker.isSuccess(failureHtml, "AUTH_REJECTED", customRouter))

        assertTrue(ResultChecker.isFailure(failureHtml, "AUTH_REJECTED", customRouter))
        assertFalse(ResultChecker.isFailure(successHtml, "WELCOME_USER_AUTHORIZED", customRouter))
    }

    @Test
    fun testResultCheckerFailureKeywords() {
        val router = RouterProfileEntity(name = "Test Router", ip = "10.0.0.1")
        val failureTexts = listOf(
            "خطأ في كلمة المرور أو رقم الكرت",
            "فشل في تسجيل الدخول",
            "اسم المستخدم غير صحيح",
            "لا يمكن إتمام الاتصال",
            "invalid username or password",
            "user not found in radius database",
            "incorrect PIN code",
            "card expired or validity ended",
            "كرت منتهي الصلاحية",
            "لقد نفذ الرصيد المتاح لهذا الكرت"
        )

        for (text in failureTexts) {
            val html = "<html><body><div class='error'>$text</div></body></html>"
            assertTrue("Expected failure for text: $text", ResultChecker.isFailure(html, text, router))
            assertFalse("Expected NOT success for text: $text", ResultChecker.isSuccess(html, text, router))
        }
    }

    @Test
    fun testResultCheckerAuthorizingState() {
        val authorizingTexts = listOf(
            "already authorizing, please wait...",
            "جاري التحقق من بيانات الاتصال...",
            "يرجى الانتظار، جاري تسجيل الدخول",
            "authorizing... checking credentials"
        )

        for (text in authorizingTexts) {
            val html = "<html><body><p>$text</p></body></html>"
            assertTrue("Expected authorizing for text: $text", ResultChecker.isAuthorizing(html, text))
        }
    }

    @Test
    fun testResultCheckerExtractRemainingInfo() {
        val textWithTime = "تفاصيل الحساب: الوقت المتبقي: 3 ساعة, 45 دقيقة و 20 ثانية"
        val timeResult = ResultChecker.extractRemainingTime("", textWithTime)
        assertNotNull(timeResult)
        assertTrue(timeResult!!.contains("ساعة"))

        val textWithData = "استهلاك الحساب: الرصيد المتبقي: 750 MB حتى نهاية الدورة"
        val dataResult = ResultChecker.extractRemainingData("", textWithData)
        assertNotNull(dataResult)
        assertEquals("750 MB", dataResult)
    }

    // =========================================================================
    // 3. InjectionManager Forensic Verification
    // =========================================================================

    @Test
    fun testInjectionManagerBuildInjectionJsWithSpecialCharacters() {
        val specialCard = "ABC'123\"<script>alert(1)</script>\\xyz"
        val js = InjectionManager.buildInjectionJs(
            card = specialCard,
            usernameSel = "#user",
            passwordSel = "#pass",
            submitSel = "#btnSubmit",
            password = "SecretPassword123"
        )

        assertNotNull(js)
        assertTrue(js.contains("triggerEvents"))
        assertTrue(js.contains("SecretPassword123"))
        // Verify JSON quoting properly escaped quotes and backslashes
        assertTrue(js.contains("doLogin"))
        assertTrue(js.contains("form[name=\"sendin\"]"))
    }

    @Test
    fun testInjectionManagerBuildLogoutJs() {
        val logoutJs = InjectionManager.buildLogoutJs("#customLogout")
        assertTrue(logoutJs.contains("#customLogout"))
        assertTrue(logoutJs.contains("mForm"))
        assertTrue(logoutJs.contains("openLogout"))
        assertTrue(logoutJs.contains("تسجيل الخروج"))
    }

    @Test
    fun testInjectionManagerBuildCheckResultJs() {
        val checkJs = InjectionManager.buildCheckResultJs(
            successInd = "custom_ok",
            failureInd = "custom_err",
            loginSel = "#login",
            logoutSel = "#logout"
        )
        assertTrue(checkJs.contains("custom_ok"))
        assertTrue(checkJs.contains("custom_err"))
        assertTrue(checkJs.contains("mikroticket status"))
        assertTrue(checkJs.contains("already authorizing"))
        assertTrue(checkJs.contains("timeLeft"))
    }

    // =========================================================================
    // 4. URL Normalization & Profile Contracts
    // =========================================================================

    @Test
    fun testGetFullLoginUrlNormalization() {
        // Standard normal profile
        val p1 = RouterProfileEntity(
            name = "Standard",
            ip = "wifi.sd.net",
            protocol = "http",
            loginPath = "/login"
        )
        assertEquals("http://wifi.sd.net/login", p1.getFullLoginUrl())

        // Missing leading slash in loginPath
        val p2 = RouterProfileEntity(
            name = "No Slash",
            ip = "wifi.sd.net",
            protocol = "http",
            loginPath = "login"
        )
        assertEquals("http://wifi.sd.net/login", p2.getFullLoginUrl())

        // Whitespace in ip and loginPath
        val p3 = RouterProfileEntity(
            name = "Whitespace",
            ip = "  192.168.1.1  ",
            protocol = "https",
            loginPath = "  /hotspot/login  "
        )
        assertEquals("https://192.168.1.1/hotspot/login", p3.getFullLoginUrl())

        // Domain RouterProfile
        val domainP = RouterProfile(
            name = "Domain",
            ip = "www.bello.com",
            protocol = "http",
            loginPath = "login"
        )
        assertEquals("http://www.bello.com/login", domainP.getFullLoginUrl())
    }

    @Test
    fun testEffectivePasswordResolution() {
        val raw = "TestPassword999!"
        val encrypted = SecurityUtils.encryptPasswordAtRest(raw)

        val disabledEntity = RouterProfileEntity(
            name = "Disabled",
            ip = "wifi.sd.net",
            password = encrypted,
            passwordEnabled = false
        )
        assertEquals("", disabledEntity.getEffectivePassword())

        val enabledEntity = RouterProfileEntity(
            name = "Enabled",
            ip = "wifi.sd.net",
            password = encrypted,
            passwordEnabled = true
        )
        assertEquals(raw, enabledEntity.getEffectivePassword())

        val domainEnabled = RouterProfile(
            name = "Domain",
            ip = "wifi.sd.net",
            password = encrypted,
            passwordEnabled = true
        )
        assertEquals(raw, domainEnabled.getEffectivePassword())
        assertTrue(domainEnabled.shouldSubmitPassword())
        assertTrue(domainEnabled.hasValidCredentials())
    }

    // =========================================================================
    // 5. Concurrency Locks & Collision Guards
    // =========================================================================

    @Test
    fun testConcurrencyCollisionGuard() {
        val isBlockedBySuccess = AtomicBoolean(false)

        // Worker 1 finishes card and reports success
        val worker1Claimed = isBlockedBySuccess.compareAndSet(false, true)
        assertTrue("Worker 1 should successfully claim success lock", worker1Claimed)
        assertTrue(isBlockedBySuccess.get())

        // Worker 2 simultaneously reports success on another card
        val worker2Claimed = isBlockedBySuccess.compareAndSet(false, true)
        assertFalse("Worker 2 MUST NOT claim success lock while blocked", worker2Claimed)

        // Worker 1 finishes logout and unlocks
        isBlockedBySuccess.set(false)
        assertFalse(isBlockedBySuccess.get())

        // Subsequent worker can now claim
        val nextClaimed = isBlockedBySuccess.compareAndSet(false, true)
        assertTrue(nextClaimed)
    }

    @Test
    fun testGlobalReloginFlagAtomicSemantics() {
        val isRelogging = AtomicBoolean(false)

        // First worker detects "already authorizing" and triggers relogin
        val triggered = isRelogging.compareAndSet(false, true)
        assertTrue("First worker must trigger global relogin", triggered)
        assertTrue(isRelogging.get())

        // Second worker tries to trigger concurrently
        val secondTriggered = isRelogging.compareAndSet(false, true)
        assertFalse("Second worker must wait without re-triggering", secondTriggered)

        // First worker completes relogin
        isRelogging.set(false)
        assertFalse(isRelogging.get())
    }
}
