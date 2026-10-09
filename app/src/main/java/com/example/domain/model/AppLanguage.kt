package com.example.domain.model

enum class AppLanguage(val code: String, val displayNameAr: String, val displayNameEn: String) {
    AR("ar", "العربية", "Arabic"),
    EN("en", "الإنجليزية", "English");

    companion object {
        val DEFAULT = AR

        fun fromCode(code: String?): AppLanguage {
            return when (code?.lowercase()) {
                "en" -> EN
                "ar" -> AR
                else -> DEFAULT
            }
        }
    }
}
