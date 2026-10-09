package com.example

import com.example.data.local.entity.RouterProfileEntity
import com.example.data.local.entity.TestResultEntity
import com.example.data.mapper.TestResultMapper.getSuccessReason
import com.example.data.mapper.TestResultMapper.getSubReason
import com.example.data.mapper.TestResultMapper.toDomain
import com.example.data.mapper.TestResultMapper.toLogEntries
import com.example.data.mapper.TestResultMapper.toDetailedLogEntries
import com.example.data.mapper.TestResultMapper.toStatistics
import com.example.domain.model.LogLevel
import com.example.domain.model.ResultCategory
import com.example.domain.model.ResultSubReason
import com.example.service.CardTestOutcome
import com.example.service.ResultChecker
import org.junit.Assert.*
import org.junit.Test

/**
 * PHASE 7 — Logs, History & Result Intelligence Test Suite.
 *
 * Verifies:
 * 1. Authoritative result model: SUCCESS, FAILURE, TIMEOUT, NETWORK_ERROR, ENGINE_ERROR.
 * 2. ResultSubReason preservation: NORMAL_LOGIN_SUCCESS, TWO_DEVICES_SUCCESS, INVALID_CARD,
 *    EXPIRED_CARD, INSUFFICIENT_BALANCE, PORTAL_REJECTED, NETWORK_TIMEOUT, DNS_ERROR,
 *    WEBVIEW_ERROR, JAVASCRIPT_ERROR, UNKNOWN.
 * 3. SuccessReason contract: NORMAL_LOGIN vs TWO_DEVICES_ACTIVE_CARD.
 * 4. Specific portal phrase "لا يمكن استعمال البطاقة في جهازين" classification as SUCCESS with
 *    TWO_DEVICES_SUCCESS and TWO_DEVICES_ACTIVE_CARD.
 * 5. Failure sub-reason classification accuracy from captive portal HTML/text responses.
 * 6. LogEntry integrity: levels (SUCCESS, WARNING, ERROR), timestamps, informative Arabic prefixes.
 * 7. Statistics aggregation: detailed breakdown and speed calculation.
 * 8. History filtering logic for All, Success, Two Devices, Failure, and Timeouts.
 */
class Phase7ResultIntelligenceTest {

    // =========================================================================
    // 1. Authoritative Result Categories & Sub-Reasons
    // =========================================================================

    @Test
    fun testCardTestOutcomeCategoriesAndSubReasons() {
        val normalSuccess = CardTestOutcome.success("تم بنجاح", 500L)
        assertEquals(ResultCategory.SUCCESS, normalSuccess.category)
        assertEquals(ResultSubReason.NORMAL_LOGIN_SUCCESS, normalSuccess.subReason)
        assertEquals("NORMAL_LOGIN", normalSuccess.successReason)
        assertTrue(normalSuccess.isSuccess)

        val twoDevSuccess = CardTestOutcome.twoDevicesSuccess()
        assertEquals(ResultCategory.SUCCESS, twoDevSuccess.category)
        assertEquals(ResultSubReason.TWO_DEVICES_SUCCESS, twoDevSuccess.subReason)
        assertEquals("TWO_DEVICES_ACTIVE_CARD", twoDevSuccess.successReason)
        assertTrue(twoDevSuccess.isSuccess)
        assertTrue(twoDevSuccess.isTwoDevicesSuccess)

        val failure = CardTestOutcome.failure("بطاقة غير صحيحة", 400L, ResultSubReason.INVALID_CARD)
        assertEquals(ResultCategory.FAILURE, failure.category)
        assertEquals(ResultSubReason.INVALID_CARD, failure.subReason)
        assertNull(failure.successReason)
        assertFalse(failure.isSuccess)

        val timeout = CardTestOutcome.timeout("مهلة استجابة", 15000L)
        assertEquals(ResultCategory.TIMEOUT, timeout.category)
        assertEquals(ResultSubReason.NETWORK_TIMEOUT, timeout.subReason)
        assertFalse(timeout.isSuccess)

        val netError = CardTestOutcome.networkError("فشل اتصال", 1000L, isDns = true)
        assertEquals(ResultCategory.NETWORK_ERROR, netError.category)
        assertEquals(ResultSubReason.DNS_ERROR, netError.subReason)
        assertFalse(netError.isSuccess)

        val engineError = CardTestOutcome.engineError("خطأ غير متوقع", 0L, isJs = true)
        assertEquals(ResultCategory.ENGINE_ERROR, engineError.category)
        assertEquals(ResultSubReason.JAVASCRIPT_ERROR, engineError.subReason)
        assertFalse(engineError.isSuccess)
    }

    // =========================================================================
    // 2. Specific Portal Condition "لا يمكن استعمال البطاقة في جهازين"
    // =========================================================================

    @Test
    fun testTwoDevicesNotificationClassifiedWithSubReason() {
        val router = RouterProfileEntity(name = "Test Router", ip = "10.0.0.1")
        val html = "<div>لا يمكن استعمال البطاقة في جهازين</div>"
        val bodyText = "لا يمكن استعمال البطاقة في جهازين"

        val outcome = ResultChecker.classifyOutcome(html, bodyText, router, 850L)
        assertTrue("Must be classified as success", outcome.isSuccess)
        assertEquals(ResultCategory.SUCCESS, outcome.category)
        assertEquals(ResultSubReason.TWO_DEVICES_SUCCESS, outcome.subReason)
        assertEquals("TWO_DEVICES_ACTIVE_CARD", outcome.successReason)
        assertTrue(outcome.message.contains("لا يمكن استعمال البطاقة في جهازين"))
    }

    // =========================================================================
    // 3. Failure Sub-Reason Classification
    // =========================================================================

    @Test
    fun testFailureSubReasonClassification() {
        // Expired card
        val expiredSub = ResultChecker.classifyFailureSubReason(
            "<html><body>الكرت منتهي الصلاحية</body></html>",
            "الكرت منتهي الصلاحية"
        )
        assertEquals(ResultSubReason.EXPIRED_CARD, expiredSub)

        // Insufficient balance
        val balanceSub = ResultChecker.classifyFailureSubReason(
            "<div>لقد نفذ الرصيد المتاح للبطاقة</div>",
            "نفذ الرصيد"
        )
        assertEquals(ResultSubReason.INSUFFICIENT_BALANCE, balanceSub)

        // Invalid credentials
        val invalidSub = ResultChecker.classifyFailureSubReason(
            "<div>اسم المستخدم أو كلمة المرور غير صحيحة</div>",
            "غير صحيحة"
        )
        assertEquals(ResultSubReason.INVALID_CARD, invalidSub)

        // Portal rejected
        val rejectedSub = ResultChecker.classifyFailureSubReason(
            "<div>تم رفض تسجيل الدخول من الخادم</div>",
            "رفض تسجيل الدخول"
        )
        assertEquals(ResultSubReason.PORTAL_REJECTED, rejectedSub)
    }

    // =========================================================================
    // 4. TestResultEntity Mapping & Sub-Reason Extraction
    // =========================================================================

    @Test
    fun testEntitySubReasonAndDomainMapping() {
        val entityTwoDevices = TestResultEntity(
            sessionId = 1L,
            cardCode = "998877",
            routerId = 1L,
            routerName = "Bello",
            state = "Success",
            message = "تم التحقق: لا يمكن استعمال البطاقة في جهازين (البطاقة نشطة)",
            durationMs = 920L
        )

        assertEquals(ResultSubReason.TWO_DEVICES_SUCCESS, entityTwoDevices.getSubReason())
        assertEquals("TWO_DEVICES_ACTIVE_CARD", entityTwoDevices.getSuccessReason())

        val domainResult = entityTwoDevices.toDomain()
        assertEquals("TWO_DEVICES_SUCCESS", domainResult.subReason)
        assertEquals("TWO_DEVICES_ACTIVE_CARD", domainResult.successReason)

        val entityExpired = TestResultEntity(
            sessionId = 1L,
            cardCode = "112233",
            routerId = 1L,
            routerName = "AlBasha",
            state = "Failure",
            message = "فشل: الكرت منتهي",
            durationMs = 450L
        )
        assertEquals(ResultSubReason.EXPIRED_CARD, entityExpired.getSubReason())
        assertNull(entityExpired.getSuccessReason())
    }

    // =========================================================================
    // 5. LogEntry Integrity & Formatting
    // =========================================================================

    @Test
    fun testLogEntriesFormattingAndLevels() {
        val results = listOf(
            TestResultEntity(
                sessionId = 1L,
                cardCode = "CARD001",
                routerId = 1L,
                routerName = "Router1",
                state = "Success",
                message = "دخول سليم",
                durationMs = 500L
            ),
            TestResultEntity(
                sessionId = 1L,
                cardCode = "CARD002",
                routerId = 1L,
                routerName = "Router1",
                state = "Success",
                message = "لا يمكن استعمال البطاقة في جهازين",
                durationMs = 600L
            ),
            TestResultEntity(
                sessionId = 1L,
                cardCode = "CARD003",
                routerId = 1L,
                routerName = "Router1",
                state = "Timeout",
                message = "انتهت المهلة",
                durationMs = 15000L
            ),
            TestResultEntity(
                sessionId = 1L,
                cardCode = "CARD004",
                routerId = 1L,
                routerName = "Router1",
                state = "Failure",
                message = "الكرت منتهي",
                durationMs = 300L
            )
        )

        // 1. Standard backwards-compatible log entries
        val standardEntries = results.toLogEntries()
        assertEquals(4, standardEntries.size)
        assertEquals("CARD001: دخول سليم", standardEntries[0].message)
        assertEquals(LogLevel.SUCCESS, standardEntries[0].level)
        assertEquals(LogLevel.ERROR, standardEntries[3].level)

        // 2. Enriched detailed log entries with classification tags
        val detailedEntries = results.toDetailedLogEntries()
        assertEquals(4, detailedEntries.size)

        // Normal success
        assertEquals(LogLevel.SUCCESS, detailedEntries[0].level)
        assertTrue(detailedEntries[0].message.contains("[تسجيل دخول ناجح]"))

        // Two devices success has distinct level SUCCESS_TWO_DEVICES
        assertEquals(LogLevel.SUCCESS_TWO_DEVICES, detailedEntries[1].level)
        assertTrue(detailedEntries[1].message.contains("[صالحة - مستعملة بجهازين]"))

        // Timeout
        assertEquals(LogLevel.WARNING, detailedEntries[2].level)
        assertTrue(detailedEntries[2].message.contains("[انتهاء المهلة]"))

        // Failure
        assertEquals(LogLevel.ERROR, detailedEntries[3].level)
        assertTrue(detailedEntries[3].message.contains("[فشل - بطاقة منتهية الصلاحية]"))
    }

    // =========================================================================
    // 6. Statistics Breakdown & Speed Accuracy
    // =========================================================================

    @Test
    fun testStatisticsAggregationBreakdown() {
        val results = listOf(
            TestResultEntity(sessionId = 1L, cardCode = "C1", routerId = 1L, routerName = "R", state = "Success", message = "تم الدخول", durationMs = 1000L),
            TestResultEntity(sessionId = 1L, cardCode = "C2", routerId = 1L, routerName = "R", state = "Success", message = "لا يمكن استعمال البطاقة في جهازين", durationMs = 1200L),
            TestResultEntity(sessionId = 1L, cardCode = "C3", routerId = 1L, routerName = "R", state = "Failure", message = "كرت غير صحيح", durationMs = 400L),
            TestResultEntity(sessionId = 1L, cardCode = "C4", routerId = 1L, routerName = "R", state = "Failure", message = "الكرت منتهي", durationMs = 500L),
            TestResultEntity(sessionId = 1L, cardCode = "C5", routerId = 1L, routerName = "R", state = "Timeout", message = "مهلة استجابة", durationMs = 15000L)
        )

        val stats = results.toStatistics()
        assertEquals(5, stats.total)
        assertEquals(2, stats.success)
        assertEquals(3, stats.failure)
        assertEquals(40f, stats.successRate, 0.01f)
        assertEquals(1, stats.normalSuccessCount)
        assertEquals(1, stats.twoDevicesSuccessCount)
        assertEquals(1, stats.invalidCardCount)
        assertEquals(1, stats.expiredCardCount)
        assertEquals(1, stats.timeoutCount)
        assertEquals(3620L, stats.avgDurationMs) // (1000 + 1200 + 400 + 500 + 15000) / 5 = 18100 / 5 = 3620
    }

    // =========================================================================
    // 7. History Filtering Logic Verification
    // =========================================================================

    @Test
    fun testHistoryFilteringCriteria() {
        val results = listOf(
            TestResultEntity(sessionId = 1L, cardCode = "C1", routerId = 1L, routerName = "R", state = "Success", message = "دخول ناجح"),
            TestResultEntity(sessionId = 1L, cardCode = "C2", routerId = 1L, routerName = "R", state = "Success", message = "لا يمكن استعمال البطاقة في جهازين"),
            TestResultEntity(sessionId = 1L, cardCode = "C3", routerId = 1L, routerName = "R", state = "Failure", message = "غير صحيح"),
            TestResultEntity(sessionId = 1L, cardCode = "C4", routerId = 1L, routerName = "R", state = "Timeout", message = "انتهت المهلة")
        )

        // All filter
        assertEquals(4, results.size)

        // Success filter
        val successes = results.filter { it.state == "Success" }
        assertEquals(2, successes.size)

        // Two devices filter
        val twoDevices = results.filter { it.state == "Success" && (it.message.contains("جهازين") || it.message.contains("TWO_DEVICES")) }
        assertEquals(1, twoDevices.size)
        assertEquals("C2", twoDevices[0].cardCode)

        // Failure filter
        val failures = results.filter { it.state != "Success" }
        assertEquals(2, failures.size)

        // Timeout filter
        val timeouts = results.filter { it.state == "Timeout" || it.state == "Network_Error" }
        assertEquals(1, timeouts.size)
        assertEquals("C4", timeouts[0].cardCode)
    }
}
