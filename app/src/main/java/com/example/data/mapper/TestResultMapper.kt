package com.example.data.mapper

import com.example.data.local.entity.TestResultEntity
import com.example.domain.model.TestResult
import com.example.domain.model.LogEntry
import com.example.domain.model.LogLevel
import com.example.domain.model.ResultSubReason
import com.example.domain.model.Statistics

object TestResultMapper {

    fun TestResultEntity.getSubReason(): ResultSubReason {
        if (subReason.isNotBlank()) {
            val parsed = ResultSubReason.fromCode(subReason)
            if (parsed != ResultSubReason.UNKNOWN || state == "Engine_Error") {
                return parsed
            }
        }
        if (state == "Success") {
            return if (message.contains("جهازين") || message.contains("TWO_DEVICES") || message.contains("جهازان")) {
                ResultSubReason.TWO_DEVICES_SUCCESS
            } else {
                ResultSubReason.NORMAL_LOGIN_SUCCESS
            }
        }
        if (state == "Timeout") return ResultSubReason.NETWORK_TIMEOUT
        if (state == "Network_Error") {
            return if (message.contains("DNS") || message.contains("عنوان")) ResultSubReason.DNS_ERROR else ResultSubReason.WEBVIEW_ERROR
        }
        if (state == "Engine_Error") return ResultSubReason.UNKNOWN

        val lowerMsg = message.lowercase()
        return when {
            lowerMsg.contains("منتهي") || lowerMsg.contains("expired") -> ResultSubReason.EXPIRED_CARD
            lowerMsg.contains("رصيد") || lowerMsg.contains("balance") || lowerMsg.contains("quota") || lowerMsg.contains("نفذ") -> ResultSubReason.INSUFFICIENT_BALANCE
            lowerMsg.contains("غير صحيح") || lowerMsg.contains("invalid") || lowerMsg.contains("not found") -> ResultSubReason.INVALID_CARD
            else -> ResultSubReason.PORTAL_REJECTED
        }
    }

    fun TestResultEntity.getSuccessReason(): String? {
        if (!successReason.isNullOrBlank()) return successReason
        if (state != "Success") return null
        return if (message.contains("جهازين") || message.contains("TWO_DEVICES") || message.contains("جهازان")) {
            "TWO_DEVICES_ACTIVE_CARD"
        } else {
            "NORMAL_LOGIN"
        }
    }

    fun TestResultEntity.toDomain(): TestResult = TestResult(
        id = id,
        sessionId = sessionId,
        cardCode = cardCode,
        routerId = routerId,
        routerName = routerName,
        state = state,
        message = message,
        durationMs = durationMs,
        testedAt = testedAt,
        subReason = getSubReason().name,
        successReason = getSuccessReason()
    )

    fun List<TestResultEntity>.toDomainList(): List<TestResult> = map { it.toDomain() }

    fun List<TestResultEntity>.toLogEntries(limit: Int = 100): List<LogEntry> {
        val source = if (size > limit) subList(size - limit, size) else this
        return source.map { entity ->
            LogEntry(
                level = if (entity.state == "Success") LogLevel.SUCCESS else LogLevel.ERROR,
                message = "${entity.cardCode}: ${entity.message}",
                timestamp = entity.testedAt
            )
        }
    }

    fun List<TestResultEntity>.toDetailedLogEntries(limit: Int = 100): List<LogEntry> {
        val source = if (size > limit) subList(size - limit, size) else this
        return source.map { entity ->
            val sub = entity.getSubReason()
            val subName = sub.displayNameAr
            val (level, prefix) = when (entity.state) {
                "Success" -> {
                    if (sub == ResultSubReason.TWO_DEVICES_SUCCESS) {
                        Pair(LogLevel.SUCCESS_TWO_DEVICES, "[صالحة - مستعملة بجهازين]")
                    } else {
                        Pair(LogLevel.SUCCESS, "[تسجيل دخول ناجح]")
                    }
                }
                "Timeout" -> Pair(LogLevel.WARNING, "[انتهاء المهلة]")
                "Network_Error" -> Pair(LogLevel.WARNING, "[خطأ اتصال]")
                "Engine_Error" -> Pair(LogLevel.ERROR, "[خطأ المحرك]")
                else -> Pair(LogLevel.ERROR, "[فشل - $subName]")
            }

            LogEntry(
                level = level,
                message = "${entity.cardCode}: $prefix ${entity.message}",
                timestamp = entity.testedAt
            )
        }
    }

    fun List<TestResultEntity>.toStatistics(): Statistics {
        val total = size
        if (total == 0) return Statistics()

        var success = 0
        var normalSuccess = 0
        var twoDevicesSuccess = 0
        var timeout = 0
        var networkError = 0
        var engineError = 0
        var invalidCard = 0
        var expiredCard = 0
        var failure = 0
        var totalDuration = 0L

        for (entity in this) {
            totalDuration += entity.durationMs
            val sub = entity.getSubReason()
            when {
                entity.state == "Success" || entity.category == "SUCCESS" -> {
                    success++
                    if (sub == ResultSubReason.TWO_DEVICES_SUCCESS) {
                        twoDevicesSuccess++
                    } else {
                        normalSuccess++
                    }
                }
                entity.state == "Timeout" || entity.category == "TIMEOUT" -> {
                    timeout++
                    failure++
                }
                entity.state == "Network_Error" || entity.category == "NETWORK_ERROR" -> {
                    networkError++
                }
                entity.state == "Engine_Error" || entity.category == "ENGINE_ERROR" -> {
                    engineError++
                }
                else -> {
                    failure++
                    when (sub) {
                        ResultSubReason.EXPIRED_CARD -> expiredCard++
                        ResultSubReason.INVALID_CARD -> invalidCard++
                        else -> {}
                    }
                }
            }
        }
        return Statistics(
            total = total,
            success = success,
            failure = failure,
            successRate = if (total > 0) (success.toFloat() / total * 100f) else 0f,
            normalSuccessCount = normalSuccess,
            twoDevicesSuccessCount = twoDevicesSuccess,
            timeoutCount = timeout,
            networkErrorCount = networkError,
            engineErrorCount = engineError,
            invalidCardCount = invalidCard,
            expiredCardCount = expiredCard,
            avgDurationMs = if (total > 0) totalDuration / total else 0L
        )
    }
}
