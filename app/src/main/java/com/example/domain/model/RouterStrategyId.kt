package com.example.domain.model

/**
 * Explicit stable machine identifier representing the router captive portal strategy.
 * Decouples strategy selection from localized display names and DNS hostnames.
 */
enum class RouterStrategyId(val id: String) {
    ABASHA("abasha"),
    BELLO("bello"),
    MOTASEM("motasem"),
    GENERIC("generic");

    companion object {
        fun fromString(value: String?): RouterStrategyId {
            return when (value?.trim()?.lowercase()) {
                "abasha", "albasha", "al-basha" -> ABASHA
                "bello" -> BELLO
                "motasem", "muatasem" -> MOTASEM
                "generic" -> GENERIC
                else -> {
                    entries.find { it.name.equals(value?.trim(), ignoreCase = true) } ?: GENERIC
                }
            }
        }
    }
}
