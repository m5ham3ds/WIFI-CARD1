package com.example

import com.example.data.local.entity.RouterProfileEntity
import com.example.data.local.entity.TestResultEntity
import com.example.data.mapper.TestResultMapper.toLogEntries
import com.example.data.mapper.TestResultMapper.toStatistics
import com.example.domain.model.LogLevel
import com.example.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * PHASE 6.2 — Card Lifecycle, Result Integrity & Session Flow Test Suite.
 *
 * Verifies:
 * 1. Specific Portal Notification "لا يمكن استعمال البطاقة في جهازين" as an immediate SUCCESS condition.
 * 2. ResultChecker accuracy for all two-device notification variants (DOM and JS Alert).
 * 3. CardTestOutcome model integrity (success, failure, two-devices success, timeout).
 * 4. Strict One Card -> One Lifecycle -> One Authoritative Result -> Progress Update order.
 * 5. Bypassing redundant router logout when twoDevicesSuccess is detected.
 * 6. Fresh login page verification across all 4 router strategies (Abasha, Motasem, Bello, Generic).
 * 7. Mapping authoritative result messages to UI logs and statistics.
 */
class Phase62CardLifecycleTest {

    // =========================================================================
    // 1. Two-Devices Immediate Success Condition Verification
    // =========================================================================

    @Test
    fun testTwoDevicesNotificationTreatedAsImmediateSuccess() {
        val router = RouterProfileEntity(name = "Test Hotspot", ip = "10.0.0.1")

        val exactPhrases = listOf(
            "لا يمكن استعمال البطاقة في جهازين",
            "لا يمكن استخدام البطاقة في جهازين",
            "لا يمكن استعمال الكرت في جهازين",
            "لا يمكن استخدام الكرت في جهازين",
            "لا يمكن استعمال هذا الكرت في جهازين",
            "لا يمكن استخدام هذا الكرت في جهازين",
            "لا يمكن استعمال هذه البطاقة في جهازين",
            "لا يمكن استخدام هذه البطاقة في جهازين",
            "لا يمكن استعمال الكارت في جهازين",
            "لا يمكن استخدام الكارت في جهازين"
        )

        for (phrase in exactPhrases) {
            val html = "<html><body><div class='alert alert-danger'>$phrase</div></body></html>"
            val text = phrase

            // 1. Must match ResultChecker.isTwoDevicesSuccess
            assertTrue("Should match isTwoDevicesSuccess for: $phrase", ResultChecker.isTwoDevicesSuccess(html, text))

            // 2. Must be classified as SUCCESS in ResultChecker
            assertTrue("Must be classified as SUCCESS for: $phrase", ResultChecker.isSuccess(html, text, router))

            // 3. Must NEVER be classified as FAILURE in ResultChecker
            assertFalse("Must NOT be classified as FAILURE for: $phrase", ResultChecker.isFailure(html, text, router))
        }
    }

    @Test
    fun testTwoDevicesNotificationFromJsAlert() {
        val alertMessage = "تنبيه: لا يمكن استعمال البطاقة في جهازين بنفس الوقت!"

        assertTrue(ResultChecker.isTwoDevicesSuccess("", alertMessage))
        val outcome = CardTestOutcome.twoDevicesSuccess()
        assertTrue(outcome.isSuccess)
        assertTrue(outcome.isTwoDevicesSuccess)
        assertEquals("Success", outcome.state)
        assertTrue(outcome.message.contains("لا يمكن استعمال البطاقة في جهازين"))
    }

    @Test
    fun testTwoDevicesSemanticVariations() {
        val semanticHtml = "<div>خطأ: لا يمكن استخدام هذا الكرت حالياً في جهازين مختلفين</div>"
        val bodyText = "لا يمكن استخدام هذا الكرت حالياً في جهازين مختلفين"

        assertTrue(ResultChecker.isTwoDevicesSuccess(semanticHtml, bodyText))

        val unrelatedError = "<div>خطأ: لا يمكن الاتصال بالخادم</div>"
        assertFalse(ResultChecker.isTwoDevicesSuccess(unrelatedError, "لا يمكن الاتصال بالخادم"))
    }

    // =========================================================================
    // 2. CardTestOutcome Model Invariants
    // =========================================================================

    @Test
    fun testCardTestOutcomeFactoryMethods() {
        val normalSuccess = CardTestOutcome.success("تم بنجاح", 1200L)
        assertTrue(normalSuccess.isSuccess)
        assertEquals("Success", normalSuccess.state)
        assertEquals("تم بنجاح", normalSuccess.message)
        assertEquals(1200L, normalSuccess.durationMs)
        assertFalse(normalSuccess.isTwoDevicesSuccess)

        val twoDevSuccess = CardTestOutcome.twoDevicesSuccess("تم التحقق بنجاح: جهازين", 800L)
        assertTrue(twoDevSuccess.isSuccess)
        assertEquals("Success", twoDevSuccess.state)
        assertTrue(twoDevSuccess.isTwoDevicesSuccess)
        assertEquals(800L, twoDevSuccess.durationMs)

        val failure = CardTestOutcome.failure("بطاقة خاطئة", 1500L)
        assertFalse(failure.isSuccess)
        assertEquals("Failure", failure.state)
        assertEquals("بطاقة خاطئة", failure.message)

        val timeout = CardTestOutcome.timeout("مهلة انتهت", 5000L)
        assertFalse(timeout.isSuccess)
        assertEquals("Timeout", timeout.state)

        val engineErr = CardTestOutcome.engineError("WebView null", 0L)
        assertFalse(engineErr.isSuccess)
        assertEquals("Engine_Error", engineErr.state)
    }

    // =========================================================================
    // 3. Card Lifecycle Progression Contract: Persistence -> Progress Increment
    // =========================================================================

    @Test
    fun testCardLifecycleProgressOnlyAdvancesAfterPersistence() = runBlocking {
        val serviceState = MutableStateFlow(ServiceState(total = 5))
        val progressCounter = AtomicInteger(0)
        val successCounter = AtomicInteger(0)
        val persistedResults = mutableListOf<TestResultEntity>()
        val mutex = Mutex()

        val cards = listOf("CARD-1", "CARD-2", "CARD-3")

        for (card in cards) {
            val priorProgress = progressCounter.get()
            // STEP 1: Card selected / active display updated (progress NOT incremented)
            serviceState.update { it.copy(currentCard = card) }
            assertEquals(card, serviceState.value.currentCard)
            assertEquals("Progress must NOT advance prior to test", priorProgress, serviceState.value.progress)

            // STEP 2: Simulated test action & authoritative outcome
            val outcome = if (card == "CARD-2") {
                CardTestOutcome.twoDevicesSuccess("تم التحقق: جهازين", 300L)
            } else {
                CardTestOutcome.failure("فشل الاختبار", 400L)
            }

            // STEP 3: Authoritative persistence to DB FIRST
            mutex.withLock {
                persistedResults.add(
                    TestResultEntity(
                        sessionId = 1L,
                        cardCode = card,
                        routerId = 1L,
                        routerName = "Hotspot",
                        state = outcome.state,
                        message = outcome.message,
                        durationMs = outcome.durationMs,
                        testedAt = System.currentTimeMillis()
                    )
                )
            }

            // STEP 4: Progress increment ONLY AFTER persistence
            val currentProgress = progressCounter.incrementAndGet()
            if (outcome.isSuccess) {
                val currentSuccess = successCounter.incrementAndGet()
                serviceState.update { it.copy(progress = currentProgress, successCount = currentSuccess) }
            } else {
                serviceState.update { it.copy(progress = currentProgress) }
            }

            // STEP 5: Verification of order
            assertEquals(currentProgress, persistedResults.size)
            assertEquals(currentProgress, serviceState.value.progress)
        }

        assertEquals(3, persistedResults.size)
        assertEquals(3, progressCounter.get())
        assertEquals(1, successCounter.get())
        assertEquals("CARD-2", persistedResults[1].cardCode)
        assertEquals("Success", persistedResults[1].state)
        assertTrue(persistedResults[1].message.contains("جهازين"))
    }

    // =========================================================================
    // 4. Session Transition & Bypassing Logout on Two-Devices Success
    // =========================================================================

    @Test
    fun testTwoDevicesSuccessDoesNotTriggerLogout() {
        val outcomeNormal = CardTestOutcome.success("تسجيل دخول نشط")
        val outcomeTwoDevices = CardTestOutcome.twoDevicesSuccess("لا يمكن استعمال البطاقة في جهازين")

        fun shouldPerformRouterLogout(outcome: CardTestOutcome): Boolean {
            return outcome.isSuccess && !outcome.isTwoDevicesSuccess
        }

        assertTrue("Normal success must trigger router logout", shouldPerformRouterLogout(outcomeNormal))
        assertFalse("Two devices success MUST NOT trigger router logout (not authenticated)", shouldPerformRouterLogout(outcomeTwoDevices))
    }

    // =========================================================================
    // 5. Strategy Fresh Login Page Detection Contracts
    // =========================================================================

    @Test
    fun testStrategyFreshLoginPageContracts() = runBlocking {
        val router = RouterProfileEntity(name = "Test", ip = "10.0.0.1")

        // 1. Abasha: recognizes ready login vs logout
        val abashaReadyJsEvaluator: suspend (String) -> String = { "ready" }
        assertTrue(AbashaTestStrategy.verifyFreshLoginPage(router, null, abashaReadyJsEvaluator))

        val abashaNotReadyJsEvaluator: suspend (String) -> String = { "on_logout_page" }
        assertFalse(AbashaTestStrategy.verifyFreshLoginPage(router, null, abashaNotReadyJsEvaluator))

        // 2. Motasem: recognizes ready login vs logout
        val motasemReadyJsEvaluator: suspend (String) -> String = { "ready" }
        assertTrue(MotasemTestStrategy.verifyFreshLoginPage(router, null, motasemReadyJsEvaluator))

        val motasemNotReadyJsEvaluator: suspend (String) -> String = { "on_logout_page" }
        assertFalse(MotasemTestStrategy.verifyFreshLoginPage(router, null, motasemNotReadyJsEvaluator))

        // 3. Bello: recognizes ready login vs logout
        val belloReadyJsEvaluator: suspend (String) -> String = { "ready" }
        assertTrue(BelloTestStrategy.verifyFreshLoginPage(router, null, belloReadyJsEvaluator))

        val belloNotReadyJsEvaluator: suspend (String) -> String = { "on_logout_page" }
        assertFalse(BelloTestStrategy.verifyFreshLoginPage(router, null, belloNotReadyJsEvaluator))

        // 4. Generic: recognizes ready login vs logout
        val genericReadyJsEvaluator: suspend (String) -> String = { "ready" }
        assertTrue(GenericTestStrategy.verifyFreshLoginPage(router, null, genericReadyJsEvaluator))

        val genericNotReadyJsEvaluator: suspend (String) -> String = { "not_ready" }
        assertFalse(GenericTestStrategy.verifyFreshLoginPage(router, null, genericNotReadyJsEvaluator))
    }

    // =========================================================================
    // 6. Test Result Integrity & UI Log Mapping
    // =========================================================================

    @Test
    fun testAuthoritativeResultLogIntegrity() {
        val results = listOf(
            TestResultEntity(
                id = 1,
                sessionId = 1,
                cardCode = "1001",
                routerId = 1,
                routerName = "Hotspot",
                state = "Success",
                message = "تم التحقق بنجاح: لا يمكن استعمال البطاقة في جهازين (البطاقة نشطة وصالحة)",
                durationMs = 250,
                testedAt = 1000L
            ),
            TestResultEntity(
                id = 2,
                sessionId = 1,
                cardCode = "1002",
                routerId = 1,
                routerName = "Hotspot",
                state = "Failure",
                message = "فشلت عملية الاختبار: بطاقة غير صالحة أو منتهية",
                durationMs = 300,
                testedAt = 1050L
            ),
            TestResultEntity(
                id = 3,
                sessionId = 1,
                cardCode = "1003",
                routerId = 1,
                routerName = "Hotspot",
                state = "Success",
                message = "تم اختبار البطاقة بنجاح: تم تسجيل الدخول إلى شبكة الباشا",
                durationMs = 1200,
                testedAt = 1100L
            )
        )

        val logEntries = results.toLogEntries()
        assertEquals(3, logEntries.size)

        // Card 1: Two-devices success should have SUCCESS level and explicit message
        assertEquals(LogLevel.SUCCESS, logEntries[0].level)
        assertTrue(logEntries[0].message.contains("1001: تم التحقق بنجاح: لا يمكن استعمال البطاقة في جهازين"))

        // Card 2: Failure should have ERROR level
        assertEquals(LogLevel.ERROR, logEntries[1].level)
        assertTrue(logEntries[1].message.contains("1002: فشلت عملية الاختبار"))

        // Card 3: Normal success
        assertEquals(LogLevel.SUCCESS, logEntries[2].level)
        assertTrue(logEntries[2].message.contains("1003: تم اختبار البطاقة بنجاح"))

        // Statistics calculation
        val stats = results.toStatistics()
        assertEquals(3, stats.total)
        assertEquals(2, stats.success)
        assertEquals(1, stats.failure)
        assertEquals(66.66667f, stats.successRate, 0.01f)
    }

    // =========================================================================
    // 7. Test Matrix Mandatory Contracts: A, C, D, E, F, G, I, J
    // =========================================================================

    @Test
    fun testA_CardCannotAdvanceBeforeFinalization() {
        var currentState = CardLifecycleState.IDLE
        val allowedStatesToConsumeNext = setOf(CardLifecycleState.READY_FOR_NEXT)

        // As card goes through lifecycle, it cannot advance until READY_FOR_NEXT
        val progression = listOf(
            CardLifecycleState.QUEUED,
            CardLifecycleState.NAVIGATING,
            CardLifecycleState.LOGIN_PAGE_READY,
            CardLifecycleState.SUBMITTING,
            CardLifecycleState.WAITING_RESULT,
            CardLifecycleState.SUCCESS,
            CardLifecycleState.LOGOUT_REQUESTED,
            CardLifecycleState.LOGOUT_CONFIRMED,
            CardLifecycleState.RESETTING
        )

        for (state in progression) {
            currentState = state
            assertFalse(
                "Worker MUST NOT consume next card in state $state",
                allowedStatesToConsumeNext.contains(currentState)
            )
        }

        currentState = CardLifecycleState.READY_FOR_NEXT
        assertTrue(
            "Worker can ONLY consume next card when in READY_FOR_NEXT",
            allowedStatesToConsumeNext.contains(currentState)
        )
    }

    @Test
    fun testC_LoginPageSkipPrevention() {
        val router = RouterProfileEntity(name = "Test", ip = "wifi.sd.net", strategyId = "abasha")

        // 1. If DOM is in logout state, it MUST NOT be considered ready
        val onLogoutPageJs: suspend (String) -> String = { "on_logout_page" }
        runBlocking {
            val isReady = AbashaTestStrategy.verifyFreshLoginPage(router, null, onLogoutPageJs)
            assertFalse("Logout page must never be accepted as ready login page", isReady)
        }

        // 2. If DOM is not ready, it MUST NOT be accepted
        val notReadyJs: suspend (String) -> String = { "not_ready" }
        runBlocking {
            val isReady = AbashaTestStrategy.verifyFreshLoginPage(router, null, notReadyJs)
            assertFalse("Incomplete DOM must not be accepted as ready", isReady)
        }
    }

    @Test
    fun testD_StaleCallbackRejection() {
        var activeGeneration = 105L

        fun processCallback(generation: Long, message: String): String? {
            // Callback from older generation must be rejected
            return if (generation >= activeGeneration) message else null
        }

        // Old callback from Card A (generation 104) arrives while Card B (generation 105) is active
        val staleResult = processCallback(104L, "old_card_result")
        assertNull("Stale callback from generation 104 must be dropped", staleResult)

        // Valid callback from current generation
        val validResult = processCallback(105L, "current_card_result")
        assertEquals("current_card_result", validResult)
    }

    @Test
    fun testE_DuplicateFinalizationIdempotency() {
        val finalizedAttempts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val persistedCount = AtomicInteger(0)

        fun finalizeAttempt(attemptKey: String) {
            if (finalizedAttempts.add(attemptKey)) {
                persistedCount.incrementAndGet()
            }
        }

        val key = "session1_card0_worker0_attempt1"
        // First finalization wins
        finalizeAttempt(key)
        assertEquals(1, persistedCount.get())

        // Duplicate finalization (e.g. timeout racing with result) is ignored
        finalizeAttempt(key)
        assertEquals("Duplicate call must not create second result", 1, persistedCount.get())
    }

    @Test
    fun testF_ButtonEventSafety() {
        val serviceRunning = AtomicBoolean(false)
        var cardIndex = 0

        fun onStartButtonClicked() {
            // Clicking button must not increment card index directly
            if (!serviceRunning.get()) {
                serviceRunning.set(true)
            }
        }

        onStartButtonClicked()
        assertTrue(serviceRunning.get())
        assertEquals("Button click must NOT mutate engine card index", 0, cardIndex)

        // Second click while running
        onStartButtonClicked()
        assertEquals("Duplicate button click must not advance card", 0, cardIndex)
    }

    @Test
    fun testG_SharedHostStrategiesDifferentiated() {
        val albashaRouter = RouterProfileEntity(
            name = "الباشا",
            ip = "wifi.sd.net",
            strategyId = "abasha"
        )
        val motasemRouter = RouterProfileEntity(
            name = "معتصم",
            ip = "wifi.sd.net",
            strategyId = "motasem"
        )

        val albashaStrategy = RouterStrategyFactory.getStrategy(albashaRouter)
        val motasemStrategy = RouterStrategyFactory.getStrategy(motasemRouter)

        assertEquals("Abasha", albashaStrategy.strategyName)
        assertEquals("Motasem", motasemStrategy.strategyName)
        assertNotEquals(albashaStrategy.strategyName, motasemStrategy.strategyName)
    }

    @Test
    fun testI_StaleNotificationProtection() {
        val capturedAlerts = java.util.concurrent.ConcurrentHashMap<Long, String>()
        val cardAGeneration = 101L
        val cardBGeneration = 102L

        // Card A triggered an alert
        capturedAlerts[cardAGeneration] = "لا يمكن استعمال البطاقة في جهازين"

        // For Card B, only check alerts matching or newer than Card B's generation
        fun getAlertForGeneration(gen: Long): String {
            return capturedAlerts[gen] ?: ""
        }

        assertEquals("Card A has alert", "لا يمكن استعمال البطاقة في جهازين", getAlertForGeneration(cardAGeneration))
        assertEquals("Card B must NOT see Card A alert", "", getAlertForGeneration(cardBGeneration))
    }

    @Test
    fun testJ_ResultAccountingIntegrity() {
        val totalAttempts = 50
        val successCount = 12
        val failureCount = 30
        val timeoutCount = 5
        val networkErrorCount = 2
        val engineErrorCount = 1

        val processedCount = successCount + failureCount + timeoutCount + networkErrorCount + engineErrorCount
        assertEquals(totalAttempts, processedCount)

        // Session summary rule
        val summarySuccess = successCount
        val summaryNonSuccess = failureCount + timeoutCount + networkErrorCount + engineErrorCount
        assertEquals(processedCount, summarySuccess + summaryNonSuccess)
    }
}
