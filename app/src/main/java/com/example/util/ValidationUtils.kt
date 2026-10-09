package com.example.util

object ValidationUtils {
    fun isValidIp(ip: String): Boolean {
        if (ip.isBlank()) return false
        val parts = ip.split(".")
        if (parts.size == 4) {
            return parts.all {
                val value = it.toIntOrNull()
                value != null && value in 0..255
            }
        }
        // Could be a hostname
        return ip.matches("^[a-zA-Z0-9.-]+$".toRegex())
    }

    fun isValidPort(port: Int): Boolean {
        return port in 1..65535
    }

    /**
     * Validates credentials according to Phase 3 Addendum rules:
     * - Username / Card is ALWAYS required.
     * - Password is REQUIRED ONLY when passwordEnabled is true.
     */
    fun isRouterCredentialValid(usernameOrCard: String, password: String, passwordEnabled: Boolean): Boolean {
        if (usernameOrCard.isBlank()) return false
        if (passwordEnabled && password.isBlank()) return false
        return true
    }
}
