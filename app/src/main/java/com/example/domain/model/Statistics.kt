package com.example.domain.model

data class Statistics(
    val total: Int = 0,
    val success: Int = 0,
    val failure: Int = 0,
    val successRate: Float = 0f,
    val normalSuccessCount: Int = 0,
    val twoDevicesSuccessCount: Int = 0,
    val timeoutCount: Int = 0,
    val networkErrorCount: Int = 0,
    val engineErrorCount: Int = 0,
    val invalidCardCount: Int = 0,
    val expiredCardCount: Int = 0,
    val avgDurationMs: Long = 0L
)

