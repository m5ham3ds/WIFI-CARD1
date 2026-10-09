package com.example.service

import com.example.data.local.entity.RouterProfileEntity
import timber.log.Timber

/**
 * Result evaluation engine for captive portal responses.
 * Provides both heuristic and profile-specific verification for success, failure, and session states.
 */
object ResultChecker {

    fun isTwoDevicesSuccess(html: String, bodyText: String): Boolean {
        val lowerHtml = html.lowercase()
        val lowerText = bodyText.lowercase()

        fun matchesPhrase(str: String): Boolean {
            if (str.contains("لا يمكن استعمال البطاقة في جهازين") ||
                str.contains("لا يمكن استخدام البطاقة في جهازين") ||
                str.contains("لا يمكن استعمال الكرت في جهازين") ||
                str.contains("لا يمكن استخدام الكرت في جهازين") ||
                str.contains("لا يمكن استعمال هذا الكرت في جهازين") ||
                str.contains("لا يمكن استخدام هذا الكرت في جهازين") ||
                str.contains("لا يمكن استعمال هذه البطاقة في جهازين") ||
                str.contains("لا يمكن استخدام هذه البطاقة في جهازين") ||
                str.contains("لا يمكن استعمال الكارت في جهازين") ||
                str.contains("لا يمكن استخدام الكارت في جهازين")
            ) {
                return true
            }

            // Semantic fallback: contains "لا يمكن" + "جهازين" + ("بطاق" or "كرت" or "كارت")
            val hasCannot = str.contains("لا يمكن") || str.contains("لايمكن")
            val hasTwoDevices = str.contains("جهازين") || str.contains("جهازان")
            val hasCardWord = str.contains("بطاق") || str.contains("كرت") || str.contains("كارت")
            return hasCannot && hasTwoDevices && hasCardWord
        }

        return matchesPhrase(lowerText) || matchesPhrase(lowerHtml)
    }

    fun isSuccess(html: String, bodyText: String, router: RouterProfileEntity): Boolean {
        // Priority 1: Specific "cannot use card on two devices" portal notification is an immediate SUCCESS condition
        if (isTwoDevicesSuccess(html, bodyText)) {
            return true
        }

        val lowerHtml = html.lowercase()
        val lowerText = bodyText.lowercase()

        // 1. Explicit profile indicator match
        if (router.successIndicator.isNotBlank() && router.successIndicator != "null") {
            if (lowerHtml.contains(router.successIndicator.lowercase())) {
                return true
            }
        }

        // 2. AlBasha (MikroTicket Status)
        if (lowerHtml.contains("mikroticket status") ||
            (lowerHtml.contains("mform") && lowerText.contains("عنوان ip")) ||
            lowerText.contains("خطة الإنترنت")
        ) {
            return true
        }

        // 3. Bello (MHTRF SYRIA)
        if ((lowerHtml.contains("id=\"card\"") || lowerHtml.contains("id='card'")) &&
            (lowerHtml.contains("id=\"timeleft\"") || lowerHtml.contains("id='timeleft'") || lowerText.contains("المتبقي من الرصيد"))
        ) {
            return true
        }

        // 4. Motasem Net (شبكة معتصم نت)
        if (lowerText.contains("تفاصيل الأستخدام") &&
            (lowerHtml.contains("timeleft") || lowerText.contains("الوقت المتبقي") || lowerText.contains("الرصيد المتبقي"))
        ) {
            return true
        }

        // 5. Universal Heuristic: Logout form/link present AND usage/connection stats present
        val hasLogout = lowerHtml.contains("logout") || lowerHtml.contains("تسجيل الخروج") || lowerHtml.contains("خروج")
        val hasStats = lowerText.contains("الوقت المتبقي") ||
                lowerText.contains("الميغبايت") ||
                lowerText.contains("الرصيد المتبقي") ||
                lowerText.contains("المتبقي") ||
                lowerText.contains("وقت الاتصال") ||
                lowerText.contains("عنوان ip")

        return hasLogout && hasStats
    }

    fun isFailure(html: String, bodyText: String, router: RouterProfileEntity): Boolean {
        // Critical: The "two devices" condition is a SUCCESS condition, never a failure!
        if (isTwoDevicesSuccess(html, bodyText)) {
            return false
        }

        val lowerHtml = html.lowercase()
        val lowerText = bodyText.lowercase()

        // 1. Explicit profile indicator match
        if (router.failureIndicator.isNotBlank() && router.failureIndicator != "null") {
            if (lowerHtml.contains(router.failureIndicator.lowercase())) {
                return true
            }
        }

        // 2. Standard captive portal error keywords
        val failureKeywords = listOf(
            "خطأ", "فشل", "غير صحيح", "invalid",
            "not found", "incorrect", "expired", "منتهي", "نفذ الرصيد"
        )
        if (failureKeywords.any { lowerText.contains(it) || lowerHtml.contains(it) }) {
            return true
        }

        // Check "لا يمكن" only if not related to two devices
        if ((lowerText.contains("لا يمكن") || lowerHtml.contains("لا يمكن")) && !lowerText.contains("جهازين") && !lowerHtml.contains("جهازين")) {
            return true
        }

        return false
    }

    fun isAuthorizing(html: String, bodyText: String): Boolean {
        val lower = (html + " " + bodyText).lowercase()
        return lower.contains("already authorizing") ||
                lower.contains("جاري التحقق") ||
                lower.contains("يرجى الانتظار") ||
                lower.contains("authorizing...")
    }

    fun isLoggedIn(html: String, bodyText: String, router: RouterProfileEntity): Boolean {
        return isSuccess(html, bodyText, router)
    }

    fun extractRemainingTime(html: String, bodyText: String): String? {
        val timeRegex = Regex("""(\d+\s*(?:يوم|ساعة|دقيقة|ثانية|أسبوع|d|h|m|s)(?:[,\s\d]*(?:يوم|ساعة|دقيقة|ثانية|أسبوع|d|h|m|s))*)""")
        val match = timeRegex.find(bodyText)
        return match?.value?.trim()
    }

    fun extractRemainingData(html: String, bodyText: String): String? {
        val dataRegex = Regex("""(\d+(?:\.\d+)?\s*(?:MB|GB|TB|ميجابايت|جيجابايت|تيرابايت|بايت|كيلوبايت))""", RegexOption.IGNORE_CASE)
        val match = dataRegex.find(bodyText)
        return match?.value?.trim()
    }

    fun classifyFailureSubReason(html: String, bodyText: String): com.example.domain.model.ResultSubReason {
        val lower = (html + " " + bodyText).lowercase()
        return when {
            lower.contains("منتهي") || lower.contains("expired") || lower.contains("انتهت صلاحية") -> com.example.domain.model.ResultSubReason.EXPIRED_CARD
            lower.contains("نفذ الرصيد") || lower.contains("رصيد غير كاف") || lower.contains("insufficient") || lower.contains("no quota") || lower.contains("لا يوجد رصيد") -> com.example.domain.model.ResultSubReason.INSUFFICIENT_BALANCE
            lower.contains("غير صحيح") || lower.contains("invalid") || lower.contains("not found") || lower.contains("اسم المستخدم غير موجود") || lower.contains("user not found") || lower.contains("كلمة المرور غير صحيحة") -> com.example.domain.model.ResultSubReason.INVALID_CARD
            lower.contains("رفض") || lower.contains("rejected") || lower.contains("unauthorized") || lower.contains("already authorizing") -> com.example.domain.model.ResultSubReason.PORTAL_REJECTED
            else -> com.example.domain.model.ResultSubReason.INVALID_CARD
        }
    }

    fun classifyOutcome(
        html: String,
        bodyText: String,
        router: RouterProfileEntity,
        durationMs: Long = 0L
    ): CardTestOutcome {
        if (isTwoDevicesSuccess(html, bodyText)) {
            return CardTestOutcome.twoDevicesSuccess(
                message = "تم التحقق بنجاح: لا يمكن استعمال البطاقة في جهازين (البطاقة نشطة وصالحة)",
                durationMs = durationMs
            )
        }
        if (isSuccess(html, bodyText, router)) {
            return CardTestOutcome.success(
                message = "تم تسجيل الدخول بنجاح على الراوتر (${router.name})",
                durationMs = durationMs
            )
        }
        if (isFailure(html, bodyText, router)) {
            val sub = classifyFailureSubReason(html, bodyText)
            val msg = when (sub) {
                com.example.domain.model.ResultSubReason.EXPIRED_CARD -> "فشلت عملية الاختبار: البطاقة منتهية الصلاحية"
                com.example.domain.model.ResultSubReason.INSUFFICIENT_BALANCE -> "فشلت عملية الاختبار: رصيد البطاقة غير كافٍ أو منتهٍ"
                com.example.domain.model.ResultSubReason.PORTAL_REJECTED -> "فشلت عملية الاختبار: تم رفض الطلب من البوابة"
                else -> "فشلت عملية الاختبار: البطاقة أو كلمة المرور غير صحيحة"
            }
            return CardTestOutcome.failure(
                message = msg,
                durationMs = durationMs,
                subReason = sub
            )
        }
        return CardTestOutcome.timeout(
            message = "انتهت مهلة استجابة الراوتر (Timeout)",
            durationMs = durationMs
        )
    }
}
