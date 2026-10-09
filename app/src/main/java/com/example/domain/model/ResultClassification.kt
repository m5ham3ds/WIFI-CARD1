package com.example.domain.model

enum class ResultCategory {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    NETWORK_ERROR,
    ENGINE_ERROR
}

enum class ResultSubReason(
    val displayNameAr: String,
    val code: String,
    val displayNameEn: String = ""
) {
    NORMAL_LOGIN_SUCCESS("تسجيل دخول ناجح", "NORMAL_LOGIN_SUCCESS", "Login Successful"),
    TWO_DEVICES_SUCCESS("البطاقة صالحة (مستعملة بجهازين)", "TWO_DEVICES_SUCCESS", "Card Valid (Active on two devices)"),
    INVALID_CARD("بطاقة / كلمة مرور غير صحيحة", "INVALID_CARD", "Invalid Card / Password"),
    EXPIRED_CARD("بطاقة منتهية الصلاحية", "EXPIRED_CARD", "Expired Card"),
    INSUFFICIENT_BALANCE("رصيد البطاقة منتهٍ أو غير كافٍ", "INSUFFICIENT_BALANCE", "Insufficient Balance / Quota"),
    PORTAL_REJECTED("رفض تسجيل الدخول من الراوتر", "PORTAL_REJECTED", "Login Rejected by Router"),
    NETWORK_TIMEOUT("انتهت مهلة استجابة الراوتر (Timeout)", "NETWORK_TIMEOUT", "Network Timeout"),
    DNS_ERROR("خطأ في الاتصال أو عنوان الراوتر (DNS)", "DNS_ERROR", "DNS Connection Error"),
    WEBVIEW_ERROR("خطأ في متصفح النظام (WebView)", "WEBVIEW_ERROR", "WebView Error"),
    JAVASCRIPT_ERROR("خطأ في استجابة كود الصفحة", "JAVASCRIPT_ERROR", "JavaScript Page Error"),
    UNKNOWN("نتيجة غير محددة", "UNKNOWN", "Unknown Result");

    companion object {
        fun fromCode(code: String?): ResultSubReason {
            if (code.isNullOrBlank()) return UNKNOWN
            return values().firstOrNull { 
                it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) 
            } ?: UNKNOWN
        }
    }
}
