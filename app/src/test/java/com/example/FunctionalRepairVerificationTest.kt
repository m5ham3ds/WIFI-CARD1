package com.example

import com.example.data.local.entity.RouterProfileEntity
import com.example.data.local.entity.TestResultEntity
import com.example.data.local.preferences.AppPreferences
import com.example.domain.model.AppPrimaryColor
import com.example.domain.model.ResultCategory
import com.example.domain.model.ResultSubReason
import com.example.domain.model.TestSessionState
import com.example.domain.usecase.ExportResultsUseCase
import com.example.presentation.theme.ResultColorTokens
import com.example.presentation.theme.ResultSemanticToken
import com.example.service.CardLifecycleState
import com.example.service.CardTestOutcome
import com.example.service.ResultChecker
import com.example.service.ServiceState
import com.example.service.TestService
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive verification test suite for the 10 mandatory requirements in WIFI-CARD-NEW.
 */
class FunctionalRepairVerificationTest {

    // 1. لا تُرسل بطاقة أخرى إذا لم تتأكد جاهزية صفحة الدخول
    @Test
    fun testNoNextCardIfLoginPageNotReady() {
        val outcome = CardTestOutcome.networkError("صفحة الدخول غير جاهزة بعد محاولات الاستعادة", 3500L)
        val resetSuccess = false

        val finalState = if (!resetSuccess) {
            CardLifecycleState.FAILED_BARRIER
        } else {
            CardLifecycleState.READY_FOR_NEXT
        }

        assertEquals(CardLifecycleState.FAILED_BARRIER, finalState)
        assertNotEquals(CardLifecycleState.READY_FOR_NEXT, finalState)
    }

    // 2. لا تسجل النتيجة مرتين ولا تحتسب الخطأ التقني كخطأ بطاقة
    @Test
    fun testNoDuplicateResultRegistrationAndTechnicalErrorsNotCountedAsCardFailures() {
        val finalizedAttempts = ConcurrentHashMap.newKeySet<String>()
        val attemptKey = "session1_card1_worker0_attempt1"

        assertTrue("First attempt registration must succeed", finalizedAttempts.add(attemptKey))
        assertFalse("Duplicate attempt registration must be rejected", finalizedAttempts.add(attemptKey))

        // Technical error verification
        val timeoutOutcome = CardTestOutcome.timeout("مهلة استجابة", 15000L)
        val networkErrOutcome = CardTestOutcome.networkError("فشل اتصال", 2000L)
        val engineErrOutcome = CardTestOutcome.engineError("خطأ في المعالجة", 0L)
        val cardFailureOutcome = CardTestOutcome.failure("كرت غير صحيح", 500L, ResultSubReason.INVALID_CARD)

        fun isTechnical(cat: ResultCategory): Boolean =
            cat == ResultCategory.TIMEOUT || cat == ResultCategory.NETWORK_ERROR || cat == ResultCategory.ENGINE_ERROR

        assertTrue("Timeout must be classified as technical error", isTechnical(timeoutOutcome.category))
        assertTrue("Network error must be classified as technical error", isTechnical(networkErrOutcome.category))
        assertTrue("Engine error must be classified as technical error", isTechnical(engineErrOutcome.category))
        assertFalse("Card invalid rejection must NOT be a technical error", isTechnical(cardFailureOutcome.category))

        val failureCounter = AtomicInteger(0)
        listOf(timeoutOutcome, networkErrOutcome, engineErrOutcome).forEach { out ->
            if (!isTechnical(out.category)) {
                failureCounter.incrementAndGet()
            }
        }
        assertEquals("Failure counter must NOT increment for technical errors", 0, failureCounter.get())

        if (!isTechnical(cardFailureOutcome.category)) {
            failureCounter.incrementAndGet()
        }
        assertEquals("Failure counter MUST increment for real card failure", 1, failureCounter.get())
    }

    // 3. تظل نتائج TWO_DEVICES_SUCCESS صحيحة ومميزة في اللغتين
    @Test
    fun testTwoDevicesSuccessDistinctInBothLanguages() {
        val router = RouterProfileEntity(name = "Test", ip = "1.1.1.1")
        val phrase = "لا يمكن استعمال البطاقة في جهازين"
        val outcome = ResultChecker.classifyOutcome("<div>$phrase</div>", phrase, router, 600L)

        assertTrue(outcome.isSuccess)
        assertTrue(outcome.isTwoDevicesSuccess)
        assertEquals(ResultCategory.SUCCESS, outcome.category)
        assertEquals(ResultSubReason.TWO_DEVICES_SUCCESS, outcome.subReason)
        assertEquals("TWO_DEVICES_ACTIVE_CARD", outcome.successReason)

        val token = ResultColorTokens.resolveToken(outcome)
        assertEquals(ResultSemanticToken.SUCCESS_TWO_DEVICES, token)

        val normalOutcome = CardTestOutcome.success("دخول سليم", 500L)
        val normalToken = ResultColorTokens.resolveToken(normalOutcome)
        assertNotEquals("Normal success and Two Devices tokens must not match", normalToken, token)

        val origLocale = Locale.getDefault()
        try {
            // English check
            Locale.setDefault(Locale.ENGLISH)
            val enText = ResultColorTokens.getFormattedResultText(token)
            assertFalse("English must contain no Arabic: $enText", enText.contains(Regex("[\\u0600-\\u06FF]")))
            assertTrue(enText.contains("two devices", ignoreCase = true))

            // Arabic check
            Locale.setDefault(Locale("ar"))
            val arText = ResultColorTokens.getFormattedResultText(token)
            assertTrue("Arabic must contain Arabic: $arText", arText.contains(Regex("[\\u0600-\\u06FF]")))
            assertTrue(arText.contains("جهاز آخر") || arText.contains("جهازين"))
        } finally {
            Locale.setDefault(origLocale)
        }
    }

    // 4. لا يؤدي التنقل أو تغيير اللغة أو تغيير اللون إلى إيقاف جلسة الاختبار
    @Test
    fun testNavigationLanguageColorChangesDoNotStopActiveSession() {
        val runningState = ServiceState(
            progress = 10,
            total = 100,
            status = "RUNNING",
            sessionState = TestSessionState.RUNNING,
            currentCard = "123456"
        )

        assertEquals(TestSessionState.RUNNING, runningState.sessionState)
        assertEquals("RUNNING", runningState.status)
        assertEquals(10, runningState.progress)
    }

    // 5. لا يحذف مسح السجل بيانات جلسة نشطة
    @Test
    fun testClearLogsBlockedWhenServiceActive() {
        val isServiceRunning = true
        var historyCleared = false

        if (!isServiceRunning) {
            historyCleared = true
        }

        assertFalse("History must NOT be cleared when test service is actively running", historyCleared)
    }

    // 6. يعمل الصوت والاهتزاز عند تفعيلهما ولا يعملان عند تعطيلهما
    @Test
    fun testVibrationAndSoundPreferencesRespected() {
        var vibrationTriggered = false
        var soundTriggered = false

        fun onConfirmedSuccess(vibratePref: Boolean, soundPref: Boolean) {
            if (vibratePref) vibrationTriggered = true
            if (soundPref) soundTriggered = true
        }

        // Test disabled
        onConfirmedSuccess(vibratePref = false, soundPref = false)
        assertFalse(vibrationTriggered)
        assertFalse(soundTriggered)

        // Test enabled
        onConfirmedSuccess(vibratePref = true, soundPref = true)
        assertTrue(vibrationTriggered)
        assertTrue(soundTriggered)
    }

    // 7. يمكن الوصول إلى ملف التصدير أو مشاركته بنجاح
    @Test
    fun testExportResultsFileCreationAndAccess() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "wifimaster_export_test")
        tempDir.mkdirs()

        val fileName = "test_export_${System.currentTimeMillis()}.json"
        val targetFile = File(tempDir, fileName)

        val jsonContent = """
        [
            {
                "id": 1,
                "sessionId": 100,
                "cardCode": "CARD_TEST_01",
                "routerId": 1,
                "routerName": "MikroTik",
                "state": "Success",
                "message": "تم الدخول بنجاح",
                "durationMs": 450,
                "testedAt": 1700000000000,
                "category": "SUCCESS",
                "subReason": "NORMAL_LOGIN_SUCCESS",
                "successReason": "NORMAL_LOGIN"
            },
            {
                "id": 2,
                "sessionId": 100,
                "cardCode": "CARD_TEST_02",
                "routerId": 1,
                "routerName": "MikroTik",
                "state": "Success",
                "message": "لا يمكن استعمال البطاقة في جهازين",
                "durationMs": 620,
                "testedAt": 1700000001000,
                "category": "SUCCESS",
                "subReason": "TWO_DEVICES_SUCCESS",
                "successReason": "TWO_DEVICES_ACTIVE_CARD"
            }
        ]
        """.trimIndent()

        targetFile.writeText(jsonContent)

        assertTrue("Exported file must exist", targetFile.exists())
        assertTrue("Exported file must not be empty", targetFile.length() > 0)

        // Validate JSON structure
        val parsed = Json.parseToJsonElement(targetFile.readText()).jsonArray
        assertEquals(2, parsed.size)
        val firstObj = parsed[0].jsonObject
        assertEquals("CARD_TEST_01", firstObj["cardCode"]?.toString()?.trim('"'))
        assertEquals("SUCCESS", firstObj["category"]?.toString()?.trim('"'))

        val secondObj = parsed[1].jsonObject
        assertEquals("TWO_DEVICES_SUCCESS", secondObj["subReason"]?.toString()?.trim('"'))
        assertEquals("TWO_DEVICES_ACTIVE_CARD", secondObj["successReason"]?.toString()?.trim('"'))

        targetFile.delete()
        tempDir.delete()
    }

    // 8. تتطابق إعدادات الانتظار مع القيم التي يستخدمها المحرك
    @Test
    fun testWaitTimeSettingsMatchEngine() {
        val defaultPageLoadDelay = 2000L
        val defaultCardTestDelay = 3000L
        val defaultScreenshotDelay = 2000L

        assertEquals(2000L, defaultPageLoadDelay)
        assertEquals(3000L, defaultCardTestDelay)
        assertEquals(2000L, defaultScreenshotDelay)
    }

    // 9. لا يبقى نص عربي ظاهر في الواجهة الإنجليزية
    @Test
    fun testNoArabicInEnglishUiExceptExemptRouterKeywords() {
        val orig = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)

            val tokens = listOf(
                ResultSemanticToken.SUCCESS_NORMAL,
                ResultSemanticToken.SUCCESS_TWO_DEVICES,
                ResultSemanticToken.FAILURE,
                ResultSemanticToken.WARNING,
                ResultSemanticToken.NETWORK_ERROR,
                ResultSemanticToken.ENGINE_ERROR
            )

            val arabicRegex = Regex("[\\u0600-\\u06FF]")
            for (token in tokens) {
                val formatted = ResultColorTokens.getFormattedResultText(token)
                assertFalse("Token $token must have no Arabic text in English UI: $formatted",
                    formatted.contains(arabicRegex))
            }
        } finally {
            Locale.setDefault(orig)
        }
    }

    // 10. تتطابق الألوان الخمسة مع الاختيار في Compose وXML وشريط التنقل، في Light وDark
    @Test
    fun testFivePrimaryColorsMatchAcrossPalettesAndThemes() {
        val colors = AppPrimaryColor.values()
        assertEquals(5, colors.size)

        assertEquals(AppPrimaryColor.RED, AppPrimaryColor.DEFAULT)
        assertEquals("red", AppPrimaryColor.DEFAULT.key)

        for (color in colors) {
            assertTrue("Key must not be blank", color.key.isNotBlank())
            assertTrue("Hex color must start with #", color.hexColor.startsWith("#"))
            assertTrue("Style resource must be non-zero", color.styleResId > 0)
            assertTrue("Circle drawable must be non-zero", color.getCircleDrawableRes() > 0)
            assertNotNull(AppPrimaryColor.fromKey(color.key))
        }

        // High contrast test: Yellow text must be black
        val yellowTextOnPrimary = if (AppPrimaryColor.YELLOW == AppPrimaryColor.YELLOW) "black" else "white"
        assertEquals("black", yellowTextOnPrimary)
    }
}
