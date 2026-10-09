package com.example.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.MessageDigest

object SecurityUtils {

    private const val MASTER_SALT = "WIFI_CARD_MASTER_SECURE_SALT_V2"
    // Salted SHA-256 hash of the default master passcode (MOHAMED564 + salt)
    private const val DEFAULT_HASH = "3f7f14d9a7deaa82ddc6b109b055fa903e022d43cb763ee198ca6db17da0bbe2"

    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies the unlock password in constant time against the salted cryptographic hash.
     * Prevents timing attacks and completely eliminates plaintext password exposure in bytecode.
     */
    fun verifyMasterPassword(input: String): Boolean {
        if (input.isEmpty()) return false
        val inputHash = sha256("$input:$MASTER_SALT")
        return MessageDigest.isEqual(
            inputHash.toByteArray(Charsets.UTF_8),
            DEFAULT_HASH.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Constant-time string equality check to prevent timing analysis attacks.
     */
    fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(
            a.toByteArray(Charsets.UTF_8),
            b.toByteArray(Charsets.UTF_8)
        )
    }

    private fun getSecurePrefs(context: Context): EncryptedSharedPreferences {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    fun encryptPassword(context: Context, password: String): String {
        if (password.isBlank()) return ""
        return try {
            val prefs = getSecurePrefs(context)
            val key = "pwd_${sha256(password).take(16)}"
            prefs.edit().putString(key, password).apply()
            key
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt password safely")
            // NEVER return plaintext password on encryption error
            ""
        }
    }

    fun decryptPassword(context: Context, key: String): String {
        if (key.isBlank()) return ""
        return try {
            val prefs = getSecurePrefs(context)
            prefs.getString(key, "") ?: ""
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt password")
            ""
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM with fail-closed security.
     * Output format: Base64(IV[12] + CiphertextWithTag).
     */
    fun encryptAesGcm(plaintext: String, secretKey: javax.crypto.SecretKey): String {
        if (plaintext.isBlank()) return ""
        return try {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
            java.util.Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt using AES-GCM")
            ""
        }
    }

    /**
     * Decrypts AES-256-GCM Base64 payload with fail-closed behavior.
     * Returns empty string on any corruption, tampering, or invalid key.
     */
    fun decryptAesGcm(ciphertextBase64: String, secretKey: javax.crypto.SecretKey): String {
        if (ciphertextBase64.isBlank()) return ""
        return try {
            val combined = java.util.Base64.getDecoder().decode(ciphertextBase64)
            if (combined.size < 12 + 16) return ""
            val iv = combined.copyOfRange(0, 12)
            val cipherBytes = combined.copyOfRange(12, combined.size)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, spec)
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt using AES-GCM (fail-closed)")
            ""
        }
    }

    /**
     * Derives a 256-bit AES SecretKey from passphrase and salt.
     */
    fun deriveAesKey(passphrase: String, salt: String = MASTER_SALT): javax.crypto.SecretKey {
        val digest = MessageDigest.getInstance("SHA-256").digest("$passphrase:$salt".toByteArray(Charsets.UTF_8))
        return javax.crypto.spec.SecretKeySpec(digest, "AES")
    }

    private const val ROUTER_STORAGE_SECRET = "WIFI_CARD_ROUTER_STORAGE_KEY_V1"

    fun getRouterStorageKey(): javax.crypto.SecretKey {
        return deriveAesKey(ROUTER_STORAGE_SECRET, MASTER_SALT)
    }

    /**
     * Checks if a string value appears to already be an encrypted token or AES-GCM ciphertext.
     */
    fun isEncrypted(value: String): Boolean {
        if (value.isBlank()) return false
        if (value.startsWith("pwd_") || value.startsWith("enc_")) return true
        return try {
            val bytes = java.util.Base64.getDecoder().decode(value)
            // AES-GCM combined payload has minimum 12 bytes IV + 16 bytes auth tag = 28 bytes
            bytes.size >= 28
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Encrypts a password for storage at rest if it is not already encrypted.
     */
    fun encryptPasswordAtRest(password: String): String {
        if (password.isBlank() || isEncrypted(password)) return password
        return encryptAesGcm(password, getRouterStorageKey())
    }

    /**
     * Decrypts a password stored at rest. Returns empty string if invalid or tampered (fail-closed).
     */
    fun decryptPasswordAtRest(ciphertextOrToken: String): String {
        if (ciphertextOrToken.isBlank()) return ""
        if (!isEncrypted(ciphertextOrToken)) {
            return ciphertextOrToken
        }
        val decrypted = decryptAesGcm(ciphertextOrToken, getRouterStorageKey())
        return if (decrypted.isNotEmpty()) decrypted else ""
    }

    /**
     * Checks if a target host is a private/local network address (RFC 1918)
     * or a trusted local router hostname.
     */
    fun isPrivateNetworkOrRouterHost(hostOrUrl: String, targetRouterIp: String? = null): Boolean {
        val host = try {
            if (hostOrUrl.contains("://")) {
                java.net.URI(hostOrUrl).host ?: hostOrUrl
            } else {
                hostOrUrl.substringBefore(":").substringBefore("/")
            }
        } catch (_: Exception) {
            hostOrUrl.substringBefore(":").substringBefore("/")
        }.trim().lowercase()

        if (host.isEmpty()) return false

        // Match against explicit target router IP/host if provided
        if (!targetRouterIp.isNullOrBlank()) {
            val cleanTarget = targetRouterIp.trim().lowercase()
            if (host == cleanTarget) return true
        }

        // Loopback and local domain suffixes
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home")) return true

        // Known router gateway hostnames
        val routerGatewayDomains = setOf(
            "routerlogin.net",
            "routerlogin.com",
            "tplinkwifi.net",
            "tplinkrepeater.net",
            "router.asus.com",
            "tendawifi.com",
            "fritz.box",
            "my.router",
            "wifi.sd.net",
            "abasha.com",
            "www.abasha.com",
            "bello.com",
            "www.bello.com",
            "r.com"
        )
        if (host in routerGatewayDomains) return true

        // Check RFC 1918 IPv4 private subnets
        val parts = host.split(".")
        if (parts.size == 4) {
            val octets = parts.mapNotNull { it.toIntOrNull() }
            if (octets.size == 4 && octets.all { it in 0..255 }) {
                val o1 = octets[0]
                val o2 = octets[1]
                return when {
                    o1 == 10 -> true
                    o1 == 172 && o2 in 16..31 -> true
                    o1 == 192 && o2 == 168 -> true
                    o1 == 127 -> true
                    o1 == 169 && o2 == 254 -> true
                    else -> false
                }
            }
        }

        return false
    }
}
