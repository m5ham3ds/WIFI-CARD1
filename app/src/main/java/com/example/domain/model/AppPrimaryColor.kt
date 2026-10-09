package com.example.domain.model

import android.graphics.Color
import com.example.R

enum class AppPrimaryColor(
    val key: String,
    val styleResId: Int,
    val hexColor: String,
    val displayNameAr: String,
    val displayNameEn: String
) {
    RED("red", R.style.Theme_WiFiCardMasterPro_Red, "#FFE53935", "أحمر", "Red"),
    BLUE("blue", R.style.Theme_WiFiCardMasterPro_Blue, "#FF1976D2", "أزرق", "Blue"),
    PURPLE("purple", R.style.Theme_WiFiCardMasterPro_Purple, "#FF5E35B1", "بنفسجي", "Purple"),
    YELLOW("yellow", R.style.Theme_WiFiCardMasterPro_Yellow, "#FFFBC02D", "أصفر", "Yellow"),
    GREEN("green", R.style.Theme_WiFiCardMasterPro_Green, "#FF2E7D32", "أخضر", "Green");

    val colorInt: Int
        get() = Color.parseColor(hexColor)

    fun getDisplayName(isArabic: Boolean): String {
        return if (isArabic) displayNameAr else displayNameEn
    }

    fun getCircleDrawableRes(): Int = when (this) {
        RED -> R.drawable.bg_circle_red
        BLUE -> R.drawable.bg_circle_blue
        PURPLE -> R.drawable.bg_circle_purple
        YELLOW -> R.drawable.bg_circle_yellow
        GREEN -> R.drawable.bg_circle_green
    }

    companion object {
        val DEFAULT = RED // Mandatory rule: Red is the default color upon first install

        fun fromKey(key: String?): AppPrimaryColor {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT
        }
    }
}
