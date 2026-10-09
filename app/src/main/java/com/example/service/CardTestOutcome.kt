package com.example.service

import com.example.domain.model.ResultCategory
import com.example.domain.model.ResultSubReason

/**
 * Authoritative terminal outcome of a single card test attempt.
 */
data class CardTestOutcome(
    val isSuccess: Boolean,
    val state: String, // "Success", "Failure", "Timeout", "Network_Error", "Engine_Error"
    val message: String,
    val durationMs: Long = 0L,
    val isTwoDevicesSuccess: Boolean = false,
    val portalMessage: String = "",
    val category: ResultCategory = if (isSuccess) ResultCategory.SUCCESS else when (state) {
        "Timeout" -> ResultCategory.TIMEOUT
        "Network_Error" -> ResultCategory.NETWORK_ERROR
        "Engine_Error" -> ResultCategory.ENGINE_ERROR
        else -> ResultCategory.FAILURE
    },
    val subReason: ResultSubReason = if (isTwoDevicesSuccess) {
        ResultSubReason.TWO_DEVICES_SUCCESS
    } else if (isSuccess) {
        ResultSubReason.NORMAL_LOGIN_SUCCESS
    } else when (state) {
        "Timeout" -> ResultSubReason.NETWORK_TIMEOUT
        "Network_Error" -> ResultSubReason.WEBVIEW_ERROR
        "Engine_Error" -> ResultSubReason.UNKNOWN
        else -> ResultSubReason.INVALID_CARD
    },
    val successReason: String? = if (isTwoDevicesSuccess) {
        "TWO_DEVICES_ACTIVE_CARD"
    } else if (isSuccess) {
        "NORMAL_LOGIN"
    } else null
) {
    companion object {
        fun success(
            message: String = "تم اختبار البطاقة بنجاح",
            durationMs: Long = 0L,
            isTwoDevices: Boolean = false,
            successReason: String = if (isTwoDevices) "TWO_DEVICES_ACTIVE_CARD" else "NORMAL_LOGIN"
        ) = CardTestOutcome(
            isSuccess = true,
            state = "Success",
            message = message,
            durationMs = durationMs,
            isTwoDevicesSuccess = isTwoDevices,
            category = ResultCategory.SUCCESS,
            subReason = if (isTwoDevices) ResultSubReason.TWO_DEVICES_SUCCESS else ResultSubReason.NORMAL_LOGIN_SUCCESS,
            successReason = successReason
        )

        fun twoDevicesSuccess(
            message: String = "تم التحقق بنجاح: لا يمكن استعمال البطاقة في جهازين (البطاقة نشطة وصالحة)",
            durationMs: Long = 0L
        ) = CardTestOutcome(
            isSuccess = true,
            state = "Success",
            message = message,
            durationMs = durationMs,
            isTwoDevicesSuccess = true,
            category = ResultCategory.SUCCESS,
            subReason = ResultSubReason.TWO_DEVICES_SUCCESS,
            successReason = "TWO_DEVICES_ACTIVE_CARD"
        )

        fun failure(
            message: String = "فشلت عملية الاختبار: البطاقة أو كلمة المرور غير صحيحة",
            durationMs: Long = 0L,
            subReason: ResultSubReason = ResultSubReason.INVALID_CARD
        ) = CardTestOutcome(
            isSuccess = false,
            state = "Failure",
            message = message,
            durationMs = durationMs,
            category = ResultCategory.FAILURE,
            subReason = subReason
        )

        fun timeout(
            message: String = "انتهت مهلة استجابة الراوتر (Timeout)",
            durationMs: Long = 0L
        ) = CardTestOutcome(
            isSuccess = false,
            state = "Timeout",
            message = message,
            durationMs = durationMs,
            category = ResultCategory.TIMEOUT,
            subReason = ResultSubReason.NETWORK_TIMEOUT
        )

        fun networkError(
            message: String = "تعذر الاتصال بصفحة الراوتر (Network Error)",
            durationMs: Long = 0L,
            isDns: Boolean = false
        ) = CardTestOutcome(
            isSuccess = false,
            state = "Network_Error",
            message = message,
            durationMs = durationMs,
            category = ResultCategory.NETWORK_ERROR,
            subReason = if (isDns) ResultSubReason.DNS_ERROR else ResultSubReason.WEBVIEW_ERROR
        )

        fun engineError(
            message: String = "خطأ غير متوقع في محرك الاختبار",
            durationMs: Long = 0L,
            isJs: Boolean = false
        ) = CardTestOutcome(
            isSuccess = false,
            state = "Engine_Error",
            message = message,
            durationMs = durationMs,
            category = ResultCategory.ENGINE_ERROR,
            subReason = if (isJs) ResultSubReason.JAVASCRIPT_ERROR else ResultSubReason.UNKNOWN
        )
    }
}
