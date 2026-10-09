package com.example.domain.model

enum class LogLevel {
    SUCCESS,
    SUCCESS_TWO_DEVICES,
    ERROR,
    WARNING,
    INFO
}

data class LogEntry(
    val level: LogLevel = LogLevel.INFO,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
