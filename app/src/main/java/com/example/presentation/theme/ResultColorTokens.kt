package com.example.presentation.theme

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.example.R
import com.example.data.local.entity.TestResultEntity
import com.example.data.mapper.TestResultMapper.getSubReason
import com.example.domain.model.LogLevel
import com.example.domain.model.ResultCategory
import com.example.domain.model.ResultSubReason
import com.example.domain.model.TestResult
import com.example.service.CardTestOutcome
import com.google.android.material.color.MaterialColors

/**
 * PHASE 7.1 — Authoritative Result Semantic Token System.
 *
 * Defines centralized semantic visual identities for test outcomes:
 * - SUCCESS_NORMAL (Green palette)
 * - SUCCESS_TWO_DEVICES (Amber / Orange palette)
 * - FAILURE (Red palette)
 * - WARNING (Yellow palette)
 * - NETWORK_ERROR (Blue / Cyan palette)
 * - ENGINE_ERROR (Rose / Magenta palette)
 *
 * All colors are guaranteed distinct from one another, from the brand primary theme color,
 * and between Light and Dark modes.
 */
enum class ResultSemanticToken(val tokenName: String) {
    SUCCESS_NORMAL("successNormal"),
    SUCCESS_TWO_DEVICES("successTwoDevices"),
    FAILURE("failure"),
    WARNING("warning"),
    NETWORK_ERROR("networkError"),
    ENGINE_ERROR("engineError");

    companion object {
        fun fromTokenName(name: String?): ResultSemanticToken? {
            if (name.isNullOrBlank()) return null
            return values().firstOrNull { it.tokenName.equals(name, ignoreCase = true) }
        }
    }
}

data class SemanticColorPalette(
    @ColorInt val successNormal: Int,
    @ColorInt val successTwoDevices: Int,
    @ColorInt val failure: Int,
    @ColorInt val warning: Int,
    @ColorInt val networkError: Int,
    @ColorInt val engineError: Int,
    @ColorInt val primaryTheme: Int
) {
    fun getColor(token: ResultSemanticToken): Int {
        return when (token) {
            ResultSemanticToken.SUCCESS_NORMAL -> successNormal
            ResultSemanticToken.SUCCESS_TWO_DEVICES -> successTwoDevices
            ResultSemanticToken.FAILURE -> failure
            ResultSemanticToken.WARNING -> warning
            ResultSemanticToken.NETWORK_ERROR -> networkError
            ResultSemanticToken.ENGINE_ERROR -> engineError
        }
    }
}

object ResultColorTokens {

    val LIGHT_PALETTE = SemanticColorPalette(
        successNormal = 0xFF2E7D32.toInt(),      // Emerald Green 800
        successTwoDevices = 0xFFE65100.toInt(),  // Deep Amber / Warm Orange 900
        failure = 0xFFD32F2F.toInt(),            // Red 700
        warning = 0xFFF57F17.toInt(),            // Yellow / Amber 900
        networkError = 0xFF0288D1.toInt(),       // Cerulean Blue 700
        engineError = 0xFFC2185B.toInt(),        // Deep Berry / Magenta 700
        primaryTheme = 0xFF5E35B1.toInt()        // Brand Deep Purple
    )

    val DARK_PALETTE = SemanticColorPalette(
        successNormal = 0xFF81C784.toInt(),      // Light Emerald Green 300
        successTwoDevices = 0xFFFFB74D.toInt(),  // Vibrant Amber / Orange 300
        failure = 0xFFEF5350.toInt(),            // Red 400
        warning = 0xFFFFEE58.toInt(),            // Yellow 300
        networkError = 0xFF4FC3F7.toInt(),       // Sky Blue 300
        engineError = 0xFFF06292.toInt(),        // Rose / Pink 300
        primaryTheme = 0xFF5E35B1.toInt()        // Brand Deep Purple
    )

    // =========================================================================
    // Authoritative Model Resolution (No arbitrary text guessing)
    // =========================================================================

    fun resolveToken(
        category: ResultCategory,
        subReason: ResultSubReason? = null
    ): ResultSemanticToken {
        return when (category) {
            ResultCategory.SUCCESS -> {
                if (subReason == ResultSubReason.TWO_DEVICES_SUCCESS) {
                    ResultSemanticToken.SUCCESS_TWO_DEVICES
                } else {
                    ResultSemanticToken.SUCCESS_NORMAL
                }
            }
            ResultCategory.FAILURE -> {
                when (subReason) {
                    ResultSubReason.NETWORK_TIMEOUT -> ResultSemanticToken.WARNING
                    ResultSubReason.DNS_ERROR, ResultSubReason.WEBVIEW_ERROR -> ResultSemanticToken.NETWORK_ERROR
                    ResultSubReason.JAVASCRIPT_ERROR -> ResultSemanticToken.ENGINE_ERROR
                    else -> ResultSemanticToken.FAILURE
                }
            }
            ResultCategory.TIMEOUT -> ResultSemanticToken.WARNING
            ResultCategory.NETWORK_ERROR -> ResultSemanticToken.NETWORK_ERROR
            ResultCategory.ENGINE_ERROR -> ResultSemanticToken.ENGINE_ERROR
        }
    }

    fun resolveToken(outcome: CardTestOutcome): ResultSemanticToken {
        return resolveToken(outcome.category, outcome.subReason)
    }

    fun resolveToken(result: TestResult): ResultSemanticToken {
        val sub = ResultSubReason.fromCode(result.subReason)
        val cat = when (result.state) {
            "Success" -> ResultCategory.SUCCESS
            "Timeout" -> ResultCategory.TIMEOUT
            "Network_Error" -> ResultCategory.NETWORK_ERROR
            "Engine_Error" -> ResultCategory.ENGINE_ERROR
            else -> ResultCategory.FAILURE
        }
        return resolveToken(cat, sub)
    }

    fun resolveToken(entity: TestResultEntity): ResultSemanticToken {
        val sub = entity.getSubReason()
        val cat = when (entity.state) {
            "Success" -> ResultCategory.SUCCESS
            "Timeout" -> ResultCategory.TIMEOUT
            "Network_Error" -> ResultCategory.NETWORK_ERROR
            "Engine_Error" -> ResultCategory.ENGINE_ERROR
            else -> ResultCategory.FAILURE
        }
        return resolveToken(cat, sub)
    }

    fun resolveToken(level: LogLevel): ResultSemanticToken? {
        return when (level) {
            LogLevel.SUCCESS -> ResultSemanticToken.SUCCESS_NORMAL
            LogLevel.SUCCESS_TWO_DEVICES -> ResultSemanticToken.SUCCESS_TWO_DEVICES
            LogLevel.ERROR -> ResultSemanticToken.FAILURE
            LogLevel.WARNING -> ResultSemanticToken.WARNING
            LogLevel.INFO -> null
        }
    }

    // =========================================================================
    // Palette & Theme Retrieval
    // =========================================================================

    fun getPalette(isDarkMode: Boolean): SemanticColorPalette {
        return if (isDarkMode) DARK_PALETTE else LIGHT_PALETTE
    }

    @AttrRes
    fun getThemeAttr(token: ResultSemanticToken): Int {
        return when (token) {
            ResultSemanticToken.SUCCESS_NORMAL -> R.attr.colorSuccessNormal
            ResultSemanticToken.SUCCESS_TWO_DEVICES -> R.attr.colorSuccessTwoDevices
            ResultSemanticToken.FAILURE -> R.attr.colorFailure
            ResultSemanticToken.WARNING -> R.attr.colorWarning
            ResultSemanticToken.NETWORK_ERROR -> R.attr.colorNetworkError
            ResultSemanticToken.ENGINE_ERROR -> R.attr.colorEngineError
        }
    }

    fun isDarkMode(context: Context): Boolean {
        return try {
            val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        } catch (e: Exception) {
            false
        }
    }

    @ColorInt
    fun getColor(token: ResultSemanticToken, isDarkMode: Boolean): Int {
        return getPalette(isDarkMode).getColor(token)
    }

    @ColorInt
    fun getColor(context: Context, token: ResultSemanticToken): Int {
        val isDark = isDarkMode(context)
        val fallbackColor = getColor(token, isDark)
        val attr = getThemeAttr(token)
        return try {
            MaterialColors.getColor(context, attr, fallbackColor)
        } catch (e: Exception) {
            fallbackColor
        }
    }

    fun getColorHex(token: ResultSemanticToken, isDarkMode: Boolean): String {
        val color = getColor(token, isDarkMode)
        return String.format("#%06X", 0xFFFFFF and color)
    }

    fun getColorHex(context: Context, token: ResultSemanticToken): String {
        val color = getColor(context, token)
        return String.format("#%06X", 0xFFFFFF and color)
    }

    // =========================================================================
    // Human-Readable Formatted Message Mapping
    // =========================================================================

    fun getFormattedResultText(token: ResultSemanticToken, rawMessage: String? = null): String {
        val isExplicitArabic = rawMessage?.any { it in '\u0600'..'\u06FF' } == true
        val isEn = !isExplicitArabic && java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
        return when (token) {
            ResultSemanticToken.SUCCESS_NORMAL -> {
                if (isEn) "✓ Card logged in successfully" else "✓ تم تسجيل البطاقة بنجاح"
            }
            ResultSemanticToken.SUCCESS_TWO_DEVICES -> {
                if (isEn) "✓ Card valid (Active on two devices)" else "✓ البطاقة صالحة لكنها مستخدمة على جهاز آخر"
            }
            ResultSemanticToken.FAILURE -> {
                if (!rawMessage.isNullOrBlank()) "✗ $rawMessage" else if (isEn) "✗ Card is invalid" else "✗ البطاقة غير صالحة"
            }
            ResultSemanticToken.WARNING -> {
                if (!rawMessage.isNullOrBlank()) "⏱ $rawMessage" else if (isEn) "⏱ Response timed out" else "⏱ انتهت مهلة الاستجابة"
            }
            ResultSemanticToken.NETWORK_ERROR -> {
                if (!rawMessage.isNullOrBlank()) "🌐 $rawMessage" else if (isEn) "🌐 Unable to connect to network" else "🌐 تعذر الاتصال بالشبكة"
            }
            ResultSemanticToken.ENGINE_ERROR -> {
                if (!rawMessage.isNullOrBlank()) "⚙️ $rawMessage" else if (isEn) "⚙️ An error occurred during test execution" else "⚙️ حدث خطأ أثناء تنفيذ الاختبار"
            }
        }
    }

    fun getFormattedResultText(context: Context, token: ResultSemanticToken, rawMessage: String? = null): String {
        return when (token) {
            ResultSemanticToken.SUCCESS_NORMAL -> context.getString(R.string.result_success_normal)
            ResultSemanticToken.SUCCESS_TWO_DEVICES -> context.getString(R.string.result_success_two_devices)
            ResultSemanticToken.FAILURE -> if (!rawMessage.isNullOrBlank()) "✗ $rawMessage" else context.getString(R.string.result_failure_default)
            ResultSemanticToken.WARNING -> if (!rawMessage.isNullOrBlank()) "⏱ $rawMessage" else context.getString(R.string.result_timeout_default)
            ResultSemanticToken.NETWORK_ERROR -> if (!rawMessage.isNullOrBlank()) "🌐 $rawMessage" else context.getString(R.string.result_network_error_default)
            ResultSemanticToken.ENGINE_ERROR -> if (!rawMessage.isNullOrBlank()) "⚙️ $rawMessage" else context.getString(R.string.result_engine_error_default)
        }
    }

    fun getFormattedResultText(entity: TestResultEntity): String {
        val token = resolveToken(entity)
        return getFormattedResultText(token, entity.message)
    }

    fun getFormattedResultText(context: Context, entity: TestResultEntity): String {
        val token = resolveToken(entity)
        return getFormattedResultText(context, token, entity.message)
    }

    fun getFormattedResultText(result: TestResult): String {
        val token = resolveToken(result)
        return getFormattedResultText(token, result.message)
    }

    fun getFormattedResultText(context: Context, result: TestResult): String {
        val token = resolveToken(result)
        return getFormattedResultText(context, token, result.message)
    }
}
