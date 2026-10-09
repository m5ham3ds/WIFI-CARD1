package com.example.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class RouterProfile(
    val id: Long = 0,
    val name: String = "",
    val ip: String = "",
    val protocol: String = "http",
    val username: String = "admin",
    val password: String = "",
    val passwordEnabled: Boolean = false,
    val strategyId: String = "generic",
    val loginPath: String = "/login",
    val usernameSelector: String = "",
    val passwordSelector: String = "",
    val submitSelector: String = "",
    val logoutSelector: String = "",
    val successIndicator: String = "",
    val failureIndicator: String = "",
    val customJs: String? = null,
    val md5Salt: String = "",
    val authType: RouterAuthType = RouterAuthType.FORM,
    val isActive: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {
    /**
     * Phase 3 Contract:
     * - username / card is ALWAYS required.
     * - password is REQUIRED ONLY when passwordEnabled == true.
     */
    fun hasValidCredentials(): Boolean {
        if (username.isBlank()) return false
        if (passwordEnabled && password.isBlank()) return false
        return true
    }

    /**
     * Phase 3 Test Engine Contract:
     * - if passwordEnabled == false -> submit username/card only
     * - if passwordEnabled == true  -> submit username/card + password
     */
    fun shouldSubmitPassword(): Boolean = passwordEnabled

    fun getEffectivePassword(): String {
        return if (passwordEnabled) com.example.util.SecurityUtils.decryptPasswordAtRest(password) else ""
    }

    fun getFullLoginUrl(): String {
        val cleanIp = ip.trim()
        val cleanPath = loginPath.trim().let { if (it.startsWith("/")) it else "/$it" }
        return "$protocol://$cleanIp$cleanPath"
    }
}
