package com.example.service

import android.os.SystemClock
import android.webkit.WebView
import com.example.data.local.entity.RouterProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Test Strategy for Motasem Net captive portals (شبكة معتصم نت).
 *
 * Implements the authentic Motasem captive portal contracts:
 * 1. Login page: Form 'login' posting to http://wifi.sd.net/login, visible username input,
 *    password input (width 0), hidden 'sendin' form, and portal's CHAP authentication function
 *    'doLogin()' utilizing 'hexMD5(\'\264\' + password + challenge)'.
 * 2. Intermediate redirect page: Displays "سيتم الآن تحويلك الى الموقع المطلوب" and refreshes/navigates
 *    to 'status.html' after approximately 4,000 ms.
 * 3. Status page: Authenticated session indicators including '#timeLeft', '.section.username',
 *    usage details ("تفاصيل الأستخدام"), and confirmed logout form targeting http://wifi.sd.net/logout.
 * 4. Two Devices Notification: Explicit "لا يمكن استعمال البطاقة في جهازين" alert/DOM message
 *    indicating a valid, active card being used on another client.
 */
object MotasemTestStrategy : RouterTestStrategy {
    override val strategyName: String = "Motasem"

    /**
     * Verifies that the current WebView DOM is on a fresh, ready-to-authenticate Motasem login page.
     * Rejects logout/status pages, intermediate redirect pages, and ensures both login DOM inputs
     * and the portal's CHAP hashing scripts (doLogin and hexMD5) are present.
     */
    override suspend fun verifyFreshLoginPage(
        router: RouterProfileEntity,
        webView: WebView?,
        evaluateJsSafely: suspend (String) -> String
    ): Boolean {
        val checkJs = """
        (function() {
            if (document.readyState !== 'complete' && document.readyState !== 'interactive') return 'not_ready';
            var html = (document.documentElement.innerHTML || '').toLowerCase();
            var bodyText = (document.body.innerText || '').toLowerCase();

            // 1. If currently on status or logout page, it is not a fresh login page
            if (document.getElementById('timeLeft') !== null ||
                html.indexOf('wifi.sd.net/logout') !== -1 ||
                html.indexOf('r.com/logout') !== -1 ||
                (html.indexOf('logout') !== -1 && (html.indexOf('status') !== -1 || bodyText.indexOf('تفاصيل الأستخدام') !== -1)) ||
                bodyText.indexOf('تفاصيل الأستخدام') !== -1 ||
                bodyText.indexOf('تفاصيل الاستخدام') !== -1) {
                return 'on_logout_page';
            }

            // 2. If currently on intermediate redirect page, not ready yet
            if (bodyText.indexOf('سيتم الآن تحويلك') !== -1 ||
                bodyText.indexOf('سيتم الان تحويلك') !== -1 ||
                html.indexOf('تحويلك الى الموقع المطلوب') !== -1 ||
                (html.indexOf('status.html') !== -1 && html.indexOf('refresh') !== -1)) {
                return 'redirecting';
            }

            // 3. Verify presence of login username input
            var u = document.querySelector('#username') ||
                    document.querySelector('form[name="login"] input[name="username"]') ||
                    document.querySelector('input[name="username"]') ||
                    (document.login && document.login.username);
            if (!u) return 'not_ready';

            // 4. Verify presence of required CHAP authentication scripts
            if (typeof doLogin !== 'function' || typeof hexMD5 !== 'function') {
                return 'not_ready';
            }

            return 'ready';
        })();
        """.trimIndent()
        val res = evaluateJsSafely(checkJs)
        return res == "ready"
    }

    override suspend fun testCard(
        card: String,
        router: RouterProfileEntity,
        webView: WebView?,
        evaluateJsSafely: suspend (String) -> String,
        pauseCondition: suspend () -> Unit,
        isPreloaded: Boolean,
        onRequiresGlobalRelogin: (suspend () -> Unit)?,
        isBlockedBySuccess: () -> Boolean
    ): Boolean {
        return testCardWithDetails(
            card = card,
            router = router,
            webView = webView,
            evaluateJsSafely = evaluateJsSafely,
            pauseCondition = pauseCondition,
            isPreloaded = isPreloaded,
            onRequiresGlobalRelogin = onRequiresGlobalRelogin,
            isBlockedBySuccess = isBlockedBySuccess
        ).isSuccess
    }

    override suspend fun testCardWithDetails(
        card: String,
        router: RouterProfileEntity,
        webView: WebView?,
        evaluateJsSafely: suspend (String) -> String,
        pauseCondition: suspend () -> Unit,
        isPreloaded: Boolean,
        onRequiresGlobalRelogin: (suspend () -> Unit)?,
        isBlockedBySuccess: () -> Boolean,
        capturedAlert: () -> String
    ): CardTestOutcome {
        val wv = webView ?: return CardTestOutcome.engineError("WebView null")
        val context = wv.context ?: return CardTestOutcome.engineError("Context null")

        return withContext(Dispatchers.Main) {
            val startTime = SystemClock.elapsedRealtime()
            try {
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                val pageLoadDelay = try {
                    prefs.getString("page_load_delay", "2000")?.toLongOrNull() ?: 2000L
                } catch (e: Exception) {
                    try { prefs.getLong("page_load_delay", 2000L) } catch (_: Exception) { 2000L }
                }
                val cardTestDelay = try {
                    prefs.getString("card_test_delay", "3000")?.toLongOrNull() ?: 3000L
                } catch (e: Exception) {
                    try { prefs.getLong("card_test_delay", 3000L) } catch (_: Exception) { 3000L }
                }

                pauseCondition()
                if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("تم مقاطعة الفحص بنجاح بطاقة أخرى")

                val url = router.getFullLoginUrl()

                // Confirm fresh login page readiness
                var isLoginReady = verifyFreshLoginPage(router, wv, evaluateJsSafely)
                if (!isLoginReady) {
                    wv.stopLoading()
                    wv.loadUrl(url)
                    val maxWait = pageLoadDelay.coerceAtLeast(500L)
                    val waitStart = SystemClock.elapsedRealtime()
                    while (SystemClock.elapsedRealtime() - waitStart < maxWait) {
                        if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")
                        delay(150L)
                        if (verifyFreshLoginPage(router, wv, evaluateJsSafely)) {
                            isLoginReady = true
                            break
                        }
                    }
                }

                if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")

                // Pre-flight check: Detect active Motasem Net session
                val preFlightJs = """
                (function() {
                    var html = document.documentElement.innerHTML.toLowerCase();
                    var bodyText = (document.body.innerText || '').toLowerCase();
                    var title = document.title.toLowerCase();

                    if (title.indexOf('شبكة معتصم نت') !== -1 && (bodyText.indexOf('تفاصيل الأستخدام') !== -1 || document.getElementById('timeLeft'))) return 'logged_in';
                    if (html.indexOf('wifi.sd.net/logout') !== -1 || html.indexOf('r.com/logout') !== -1 || document.querySelector('form[action*="logout"]')) return 'logged_in';
                    if (bodyText.indexOf('تفاصيل الأستخدام') !== -1 || bodyText.indexOf('الوقت المتبقي') !== -1 || bodyText.indexOf('الرصيد المتبقي') !== -1) return 'logged_in';
                    if (document.querySelector('.section.username, .section.remain')) return 'logged_in';

                    return 'login_page';
                })();
                """.trimIndent()

                val preFlightResult = evaluateJsSafely(preFlightJs)
                if (preFlightResult == "logged_in") {
                    if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")
                    Timber.d("[Motasem] Pre-flight showed ALREADY LOGGED IN. Forcing logout...")
                    if (onRequiresGlobalRelogin != null) {
                        onRequiresGlobalRelogin()
                    } else {
                        val forceLogoutJs = """
                        (function() {
                            var f = document.querySelector('form[action*="logout"], form[name="logout"]');
                            if (f) { f.submit(); return 'form_logout'; }
                            var btn = document.querySelector('form[name="logout"] button, .submit button, input[value*="تسجيل الخروج"]');
                            if (btn) { btn.click(); return 'clicked'; }
                            if (typeof openLogout === 'function') { openLogout(); return 'openLogout'; }
                            window.location.href = '${router.protocol}://${router.ip}/logout';
                            return 'navigated';
                        })();
                        """.trimIndent()
                        evaluateJsSafely(forceLogoutJs)

                        delay(1500L)
                        if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")
                        wv.loadUrl(url)
                        delay(1000L)
                    }
                }

                // Wait for login form and required CHAP functions to be fully ready
                val checkReadyJs = """
                (function() {
                    if (document.readyState !== 'complete' && document.readyState !== 'interactive') return 'not_ready';
                    var u = document.querySelector('#username') || 
                            document.querySelector('form[name="login"] input[name="username"]') || 
                            document.querySelector('input[name="username"]') || 
                            (document.login && document.login.username);
                    if (!u) return 'not_ready';
                    if (typeof doLogin !== 'function' || typeof hexMD5 !== 'function') return 'not_ready';
                    return 'ready';
                })();
                """.trimIndent()

                val maxFormWaitMs = pageLoadDelay.coerceAtLeast(500L)
                val formStartTime = SystemClock.elapsedRealtime()
                while (SystemClock.elapsedRealtime() - formStartTime < maxFormWaitMs) {
                    if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")
                    val readyState = evaluateJsSafely(checkReadyJs)
                    if (readyState == "ready") break
                    delay(150L)
                }

                if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")

                // Card Normalization: Trim surrounding whitespace without changing valid internal characters
                val normalizedCard = card.trim()
                val safeCardJson = InjectionManager.quote(normalizedCard)
                val effectivePassword = if (router.passwordEnabled) router.getEffectivePassword() else ""
                val safePasswordJson = InjectionManager.quote(effectivePassword)

                // Motasem CHAP Login Contract:
                // Strictly fills document.login and invokes the portal's original doLogin() function.
                // NEVER falls back to submitting raw/un-hashed credentials through hidden sendin form,
                // and NEVER bypasses onsubmit by calling form.submit() directly.
                val injectionJs = """
                (function() {
                    try {
                        function triggerEvents(el) {
                            if (!el) return;
                            try {
                                var ev1 = document.createEvent('Event'); ev1.initEvent('input', true, true); el.dispatchEvent(ev1);
                                var ev2 = document.createEvent('Event'); ev2.initEvent('change', true, true); el.dispatchEvent(ev2);
                            } catch(e) {}
                        }

                        var cardVal = $safeCardJson;
                        var pwdVal = $safePasswordJson;

                        // 1. Verify required CHAP functions
                        if (typeof doLogin !== 'function') {
                            return 'error: CHAP doLogin function is missing on portal page';
                        }
                        if (typeof hexMD5 !== 'function') {
                            return 'error: CHAP hexMD5 hashing function is missing on portal page';
                        }

                        // 2. Set username on visible login form
                        var uInput = (document.login && document.login.username) ||
                                     document.querySelector('form[name="login"] input[name="username"]') ||
                                     document.querySelector('#username') ||
                                     document.querySelector('input[name="username"]:not([type="hidden"])');
                        if (!uInput) {
                            return 'error: Username input not found on portal page';
                        }
                        uInput.value = cardVal;
                        triggerEvents(uInput);

                        // 3. Set password on visible login form (empty string when password use is disabled)
                        var pInput = (document.login && document.login.password) ||
                                     document.querySelector('form[name="login"] input[name="password"]') ||
                                     document.querySelector('input[type="password"]');
                        if (pInput) {
                            pInput.value = pwdVal;
                            triggerEvents(pInput);
                        }

                        // 4. Invoke portal's original doLogin() strictly
                        // Never bypass CHAP or fall back to raw sendin submission
                        try {
                            doLogin();
                            return 'injected_dologin';
                        } catch(chapErr) {
                            return 'error: doLogin execution threw: ' + (chapErr.message || chapErr);
                        }
                    } catch(e) {
                        return 'error: ' + (e.message || e);
                    }
                })();
                """.trimIndent()

                if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")
                val injectResult = evaluateJsSafely(injectionJs)
                Timber.d("[Motasem] Injection result: $injectResult")

                // If CHAP injection encountered a typed script error, return typed engine error immediately
                if (injectResult.startsWith("error:")) {
                    val duration = SystemClock.elapsedRealtime() - startTime
                    Timber.e("[Motasem] Injection failed with typed error: $injectResult")
                    return@withContext CardTestOutcome.engineError(
                        message = "خطأ في دالة تشفير البوابة (CHAP): $injectResult",
                        durationMs = duration,
                        isJs = true
                    )
                }

                // Result classification script implementing centralized precedence:
                // 1. Explicit Two Devices Notification (DOM / alert) -> 'success_two_devices'
                // 2. Authoritative Status DOM (#timeLeft, .section.username, usage details) -> 'status_success'
                // 3. Confirmed Portal Failure / Rejection -> 'failure'
                // 4. Authorizing Intermediate State -> 'authorizing'
                // 5. Intermediate Redirect Page ("سيتم الآن تحويلك الى الموقع المطلوب", meta refresh 4s) -> 'redirecting'
                // 6. Unrecognized / pending -> 'unknown'
                val checkJs = """
                (function() {
                    try {
                        var html = (document.documentElement.innerHTML || '').toLowerCase();
                        var bodyText = (document.body.innerText || '').toLowerCase();
                        var title = (document.title || '').toLowerCase();

                        // 1. Priority 1: Explicit Two Devices Notification
                        if (bodyText.indexOf('لا يمكن استعمال البطاقة في جهازين') !== -1 ||
                            bodyText.indexOf('لا يمكن استخدام البطاقة في جهازين') !== -1 ||
                            bodyText.indexOf('لا يمكن استعمال الكرت في جهازين') !== -1 ||
                            bodyText.indexOf('لا يمكن استخدام الكرت في جهازين') !== -1 ||
                            html.indexOf('لا يمكن استعمال البطاقة في جهازين') !== -1 ||
                            html.indexOf('لا يمكن استخدام البطاقة في جهازين') !== -1 ||
                            html.indexOf('لا يمكن استعمال الكرت في جهازين') !== -1 ||
                            html.indexOf('لا يمكن استخدام الكرت في جهازين') !== -1 ||
                            ((bodyText.indexOf('لا يمكن') !== -1 || html.indexOf('لا يمكن') !== -1) && 
                             (bodyText.indexOf('جهازين') !== -1 || html.indexOf('جهازين') !== -1) && 
                             (bodyText.indexOf('بطاق') !== -1 || bodyText.indexOf('كرت') !== -1 || html.indexOf('بطاق') !== -1 || html.indexOf('كرت') !== -1))) {
                            return 'success_two_devices';
                        }

                        // 2. Priority 2: Authoritative Motasem Status Page (Normal Login Success)
                        var hasTimeLeft = document.getElementById('timeLeft') !== null;
                        var hasUsernameSection = document.querySelector('.section.username') !== null;
                        var hasUsageDetails = bodyText.indexOf('تفاصيل الأستخدام') !== -1 || bodyText.indexOf('تفاصيل الاستخدام') !== -1;
                        var hasLogoutLink = html.indexOf('wifi.sd.net/logout') !== -1 || html.indexOf('r.com/logout') !== -1 || document.querySelector('form[action*="logout"]') !== null;

                        if (hasTimeLeft && (hasUsernameSection || hasUsageDetails || hasLogoutLink)) {
                            return 'status_success';
                        }
                        if (hasUsageDetails && (bodyText.indexOf('الوقت المتبقي') !== -1 || bodyText.indexOf('الرصيد المتبقي') !== -1)) {
                            return 'status_success';
                        }
                        if (title.indexOf('شبكة معتصم نت') !== -1 && (hasTimeLeft || hasUsageDetails)) {
                            return 'status_success';
                        }
                        if (document.querySelector('.section.remain') && html.indexOf('readablizebytes') !== -1) {
                            return 'status_success';
                        }

                        // 3. Priority 3: Confirmed Portal Failure / Rejection
                        var failureKeywords = ['خطأ', 'فشل', 'غير صحيح', 'invalid', 'not found', 'incorrect', 'expired', 'منتهي', 'نفذ الرصيد', 'رفض'];
                        for (var i = 0; i < failureKeywords.length; i++) {
                            if (bodyText.indexOf(failureKeywords[i]) !== -1 || html.indexOf(failureKeywords[i]) !== -1) {
                                return 'failure';
                            }
                        }
                        if ((bodyText.indexOf('لا يمكن') !== -1 || html.indexOf('لا يمكن') !== -1) && 
                            bodyText.indexOf('جهازين') === -1 && html.indexOf('جهازين') === -1) {
                            return 'failure';
                        }

                        // 4. Priority 4: Authorizing Intermediate State
                        if (bodyText.indexOf('already authorizing') !== -1 || html.indexOf('already authorizing') !== -1 ||
                            bodyText.indexOf('جاري التحقق') !== -1 || bodyText.indexOf('يرجى الانتظار') !== -1) {
                            return 'authorizing';
                        }

                        // 5. Priority 5: Intermediate Redirect Page (4s delay navigating to status.html)
                        if (bodyText.indexOf('سيتم الآن تحويلك') !== -1 || 
                            bodyText.indexOf('سيتم الان تحويلك') !== -1 ||
                            html.indexOf('سيتم الآن تحويلك') !== -1 ||
                            html.indexOf('سيتم الان تحويلك') !== -1 ||
                            html.indexOf('تحويلك الى الموقع المطلوب') !== -1 ||
                            html.indexOf('تحويلك إلى الموقع المطلوب') !== -1 ||
                            html.indexOf('content="4;url=status.html"') !== -1 ||
                            (html.indexOf('status.html') !== -1 && html.indexOf('refresh') !== -1)) {
                            return 'redirecting';
                        }

                        return 'unknown';
                    } catch(e) {
                        return 'error: ' + (e.message || e);
                    }
                })();
                """.trimIndent()

                // State-aware bounded timing formula:
                // - Normal wait: baseWaitMs = cardTestDelay.coerceAtLeast(1000L) (default 3000ms from user preference)
                // - If intermediate redirect page is detected: grant bounded redirect grace period of 7,000 ms
                //   (sufficient for the observed 4-second delay + navigation + DOM rendering headroom).
                // - Hard ceiling: maxAbsoluteDeadlineMs = 15,000 ms to guarantee termination under all circumstances.
                val baseWaitMs = cardTestDelay.coerceAtLeast(1000L)
                val redirectGraceMs = 7000L
                val maxAbsoluteDeadlineMs = 15000L

                val loopStartTime = SystemClock.elapsedRealtime()
                var redirectDetectedAt: Long? = null
                var finalState = "unknown"

                delay(150L) // Initial yield for form dispatch

                while (true) {
                    if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")

                    val now = SystemClock.elapsedRealtime()
                    val totalElapsed = now - loopStartTime

                    // 1. Check synchronous captured alert
                    val alertText = capturedAlert()
                    if (ResultChecker.isTwoDevicesSuccess("", alertText)) {
                        finalState = "success_two_devices"
                        break
                    }

                    // 2. Evaluate DOM state
                    val domState = evaluateJsSafely(checkJs)

                    when (domState) {
                        "success_two_devices" -> {
                            finalState = "success_two_devices"
                            break
                        }
                        "status_success" -> {
                            finalState = "status_success"
                            break
                        }
                        "failure" -> {
                            finalState = "failure"
                            break
                        }
                        "authorizing" -> {
                            delay(1000L)
                            val recheck = evaluateJsSafely(checkJs)
                            if (recheck == "status_success" || recheck == "success_two_devices" || recheck == "failure") {
                                finalState = recheck
                                break
                            }
                            if (recheck == "authorizing") {
                                onRequiresGlobalRelogin?.invoke()
                                finalState = "authorizing"
                                break
                            }
                        }
                        "redirecting" -> {
                            if (redirectDetectedAt == null) {
                                redirectDetectedAt = now
                                Timber.d("[Motasem] Intermediate redirect page detected ('سيتم الآن تحويلك'). Granting redirect grace period...")
                            }
                            // Do NOT call stopLoading() or force reload while redirect is legitimately in progress!
                        }
                    }

                    // Check timeout condition
                    val isExpired = if (redirectDetectedAt != null) {
                        val redirectElapsed = now - redirectDetectedAt
                        redirectElapsed >= redirectGraceMs || totalElapsed >= maxAbsoluteDeadlineMs
                    } else {
                        totalElapsed >= baseWaitMs
                    }

                    if (isExpired) {
                        finalState = if (redirectDetectedAt != null) "redirect_timeout" else "timeout"
                        break
                    }

                    delay(150L)
                }

                val duration = SystemClock.elapsedRealtime() - startTime
                Timber.d("[Motasem] Final state evaluated: $finalState (duration: ${duration}ms, redirectDetected: ${redirectDetectedAt != null})")

                return@withContext when (finalState) {
                    "success_two_devices" -> CardTestOutcome.twoDevicesSuccess(
                        message = "تم التحقق بنجاح: لا يمكن استعمال البطاقة في جهازين (البطاقة صالحة ونشطة)",
                        durationMs = duration
                    )
                    "status_success" -> CardTestOutcome.success(
                        message = "تم اختبار البطاقة بنجاح: تم تسجيل الدخول إلى شبكة معتصم نت",
                        durationMs = duration
                    )
                    "failure" -> CardTestOutcome.failure(
                        message = "فشلت عملية الاختبار: بطاقة غير صالحة أو منتهية",
                        durationMs = duration
                    )
                    "redirect_timeout" -> CardTestOutcome.timeout(
                        message = "انتهت مهلة استجابة بوابة معتصم نت أثناء التحويل إلى صفحة الحالة (Redirect Timeout)",
                        durationMs = duration
                    )
                    else -> CardTestOutcome.timeout(
                        message = "انتهت مهلة استجابة بوابة معتصم نت (Timeout)",
                        durationMs = duration
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val duration = SystemClock.elapsedRealtime() - startTime
                Timber.e(e, "Error in Motasem specific test")
                return@withContext CardTestOutcome.engineError("خطأ غير متوقع: ${e.localizedMessage}", duration)
            }
        }
    }
}
