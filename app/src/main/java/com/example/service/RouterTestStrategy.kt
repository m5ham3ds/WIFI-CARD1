package com.example.service

import android.webkit.WebView
import com.example.data.local.entity.RouterProfileEntity
import com.example.domain.model.RouterProfile
import com.example.domain.model.RouterStrategyId

/**
 * Standard polymorphic strategy contract for captive portal and router authentication testing.
 */
interface RouterTestStrategy {
    val strategyName: String

    suspend fun testCard(
        card: String,
        router: RouterProfileEntity,
        webView: WebView?,
        evaluateJsSafely: suspend (String) -> String,
        pauseCondition: suspend () -> Unit,
        isPreloaded: Boolean = false,
        onRequiresGlobalRelogin: (suspend () -> Unit)? = null,
        isBlockedBySuccess: () -> Boolean = { false }
    ): Boolean

    suspend fun testCardWithDetails(
        card: String,
        router: RouterProfileEntity,
        webView: WebView?,
        evaluateJsSafely: suspend (String) -> String,
        pauseCondition: suspend () -> Unit,
        isPreloaded: Boolean = false,
        onRequiresGlobalRelogin: (suspend () -> Unit)? = null,
        isBlockedBySuccess: () -> Boolean = { false },
        capturedAlert: () -> String = { "" }
    ): CardTestOutcome {
        val res = testCard(
            card = card,
            router = router,
            webView = webView,
            evaluateJsSafely = evaluateJsSafely,
            pauseCondition = pauseCondition,
            isPreloaded = isPreloaded,
            onRequiresGlobalRelogin = onRequiresGlobalRelogin,
            isBlockedBySuccess = isBlockedBySuccess
        )
        return if (res) CardTestOutcome.success() else CardTestOutcome.failure()
    }

    suspend fun verifyFreshLoginPage(
        router: RouterProfileEntity,
        webView: WebView?,
        evaluateJsSafely: suspend (String) -> String
    ): Boolean {
        val checkJs = """
        (function() {
            if (document.readyState !== 'complete' && document.readyState !== 'interactive') return 'not_ready';
            var html = (document.documentElement.innerHTML || '').toLowerCase();
            var title = (document.title || '').toLowerCase();
            if (title.indexOf('status') !== -1 || html.indexOf('logout') !== -1 || html.indexOf('تسجيل الخروج') !== -1) {
                return 'on_logout_page';
            }
            var u = document.querySelector('input[name="username"]') ||
                    document.querySelector('#username') ||
                    document.querySelector('#uname') ||
                    document.querySelector('input[type="text"]:not([type="hidden"])');
            return u ? 'ready' : 'not_ready';
        })();
        """.trimIndent()
        val res = evaluateJsSafely(checkJs)
        return res == "ready"
    }
}

object RouterStrategyFactory {

    /**
     * Authoritative routing based on explicit strategy enum identity.
     */
    fun getStrategy(strategyId: RouterStrategyId): RouterTestStrategy {
        return when (strategyId) {
            RouterStrategyId.ABASHA -> AbashaTestStrategy
            RouterStrategyId.BELLO -> BelloTestStrategy
            RouterStrategyId.MOTASEM -> MotasemTestStrategy
            RouterStrategyId.GENERIC -> GenericTestStrategy
        }
    }

    /**
     * Direct string resolution of strategy ID ("abasha", "bello", "motasem", "generic").
     */
    fun getStrategy(strategyId: String?): RouterTestStrategy {
        return getStrategy(strategyId = strategyId, name = "", ip = "")
    }

    /**
     * Primary factory resolution method for RouterProfileEntity.
     * Evaluates explicit strategyId first, with deterministic legacy fallback.
     */
    fun getStrategy(router: RouterProfileEntity): RouterTestStrategy {
        return getStrategy(strategyId = router.strategyId, name = router.name, ip = router.ip)
    }

    /**
     * Primary factory resolution method for domain RouterProfile.
     */
    fun getStrategy(router: RouterProfile): RouterTestStrategy {
        return getStrategy(strategyId = router.strategyId, name = router.name, ip = router.ip)
    }

    /**
     * Core resolution logic implementing the Phase 4.1 routing contract:
     * 1. Authoritative explicit strategy identity (strategyId).
     * 2. ALBASHA and MOTASEM coexisting on 'wifi.sd.net' MUST NEVER be inferred solely from host.
     * 3. Fallback to name/host only for legacy records where strategyId is missing or generic.
     */
    fun getStrategy(
        strategyId: String?,
        name: String = "",
        ip: String = ""
    ): RouterTestStrategy {
        // 1. Authoritative Strategy Identity Check
        val parsedId = RouterStrategyId.fromString(strategyId)
        if (parsedId != RouterStrategyId.GENERIC) {
            return getStrategy(parsedId)
        }

        // 2. Legacy Disambiguation Fallback (only reached if strategyId is generic or blank)
        val lowerName = name.trim().lowercase()
        val lowerIp = ip.trim().lowercase()

        // Explicit name matching for legacy unmigrated records
        if (lowerName.contains("معتصم") || lowerName.contains("motasem")) {
            return MotasemTestStrategy
        }
        if (lowerName.contains("اباشا") || lowerName.contains("abasha") || lowerName.contains("الباشا")) {
            return AbashaTestStrategy
        }
        if (lowerName.contains("بيلو") || lowerName.contains("bello")) {
            return BelloTestStrategy
        }

        // Host-based fallback: strictly allowed ONLY when the host uniquely identifies a protocol.
        // For shared host 'wifi.sd.net', host-only fallback is STRICTLY FORBIDDEN because
        // it cannot differentiate between ALBASHA and MOTASEM.
        if (lowerIp.contains("bello.com")) {
            return BelloTestStrategy
        }
        if (lowerIp.contains("abasha.com")) {
            return AbashaTestStrategy
        }
        if (lowerIp.contains("r.com")) {
            return MotasemTestStrategy
        }

        return GenericTestStrategy
    }
}
