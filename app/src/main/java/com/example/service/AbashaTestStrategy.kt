package com.example.service

import android.os.SystemClock
import android.webkit.WebView
import com.example.data.local.entity.RouterProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/**
 * Test Strategy for ALBASHA.NET captive portals supported by MikroTicket.
 * Matches portals with http://www.Abasha.com/login and MikroTicket Status logout pages.
 */
object AbashaTestStrategy : RouterTestStrategy {
    override val strategyName: String = "Abasha"

    override suspend fun verifyFreshLoginPage(
        router: RouterProfileEntity,
        webView: WebView?,
        evaluateJsSafely: suspend (String) -> String
    ): Boolean {
        val checkJs = """
        (function() {
            var html = document.documentElement.innerHTML.toLowerCase();
            var title = document.title.toLowerCase();
            if (title.indexOf('mikroticket status') !== -1 || html.indexOf('mikroticket status') !== -1 || document.getElementById('mForm')) {
                return 'on_logout_page';
            }
            var u = document.querySelector('form[name="login"] input[name="username"]') || 
                    document.querySelector('form[name="login"] input[type="text"]') ||
                    document.querySelector('input[name="username"]:not([type="hidden"])') ||
                    document.querySelector('.input-text');
            return u ? 'ready' : 'not_ready';
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

                // Pre-flight check: Detects if already logged in prior to test
                val preFlightJs = """
                (function() {
                    var html = document.documentElement.innerHTML.toLowerCase();
                    var bodyText = (document.body.innerText || '').toLowerCase();
                    var title = document.title.toLowerCase();
                    
                    if (title.indexOf('mikroticket status') !== -1 || html.indexOf('mikroticket status') !== -1) return 'logged_in';
                    if (document.getElementById('mForm') || document.getElementById('infoTable')) return 'logged_in';
                    if (html.indexOf('abasha.com/logout') !== -1 || html.indexOf('openlogout()') !== -1) return 'logged_in';
                    if (bodyText.indexOf('عنوان ip') !== -1 || bodyText.indexOf('وقت الاتصال') !== -1 || bodyText.indexOf('البيانات المتبقية') !== -1) return 'logged_in';
                    if (document.querySelector('form[action*="logout"], button[onclick*="mForm"]')) return 'logged_in';
                    
                    return 'login_page';
                })();
                """.trimIndent()

                val preFlightResult = evaluateJsSafely(preFlightJs)
                if (preFlightResult == "logged_in") {
                    if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")
                    Timber.d("[Abasha] Pre-flight showed ALREADY LOGGED IN. Forcing logout...")
                    if (onRequiresGlobalRelogin != null) {
                        onRequiresGlobalRelogin()
                    } else {
                        val forceLogoutJs = """
                        (function() {
                            var mForm = document.getElementById('mForm');
                            if (mForm) { mForm.submit(); return 'mForm'; }
                            var f = document.querySelector('form[action*="logout"], form[name="logout"]');
                            if (f) { f.submit(); return 'logout_form'; }
                            var btn = document.querySelector('button.btn-main, button[onclick*="mForm"], button[onclick*="logout"], a[href*="logout"]');
                            if (btn) { btn.click(); return 'clicked'; }
                            if (typeof openLogout === 'function') { openLogout(); return 'openLogout'; }
                            window.location.href = '${router.protocol}://${router.ip}/logout';
                            return 'navigated';
                        })();
                        """.trimIndent()
                        evaluateJsSafely(forceLogoutJs)
                        delay(1500)
                        wv.loadUrl(url)
                        delay(1000)
                    }
                }

                if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")

                val safeCard = JSONObject.quote(card).removeSurrounding("\"").replace("'", "\\'")
                val safePassword = if (router.passwordEnabled) JSONObject.quote(router.getEffectivePassword()).removeSurrounding("\"").replace("'", "\\'") else ""

                // Injection: clear previous DOM alerts, then fill and submit
                val injectionJs = """
                (function() {
                    try {
                        // Purge any stale alert/error containers from previous card
                        try {
                            var oldAlerts = document.querySelectorAll('.alert, .error, .alert-back, .alert1, .alert2, .alert3, #danger');
                            for (var i = 0; i < oldAlerts.length; i++) {
                                oldAlerts[i].style.display = 'none';
                            }
                        } catch(e) {}

                        function triggerEvents(el) {
                            if(!el) return;
                            try {
                                var ev1 = document.createEvent('Event'); ev1.initEvent('input', true, true); el.dispatchEvent(ev1);
                                var ev2 = document.createEvent('Event'); ev2.initEvent('change', true, true); el.dispatchEvent(ev2);
                            } catch(e) {}
                        }

                        var cardValue = '$safeCard';
                        var passwordValue = '$safePassword';

                        // 1. Fill visible username/card field
                        var u = document.querySelector('form[name="login"] input[name="username"]') || 
                                document.querySelector('input[name="username"]:not([type="hidden"])') ||
                                document.querySelector('.input-text');
                        if (u) {
                            u.value = cardValue;
                            triggerEvents(u);
                        }

                        // 2. Fill password field if provided
                        var p = document.querySelector('form[name="login"] input[name="password"]') || 
                                document.querySelector('#userInput input[name="password"]') ||
                                document.querySelector('input[name="password"]');
                        if (p) {
                            p.value = passwordValue;
                            triggerEvents(p);
                        }

                        // 3. Set hidden sendin form directly
                        var sendin = document.querySelector('form[name="sendin"]');
                        if (sendin) {
                            var su = sendin.querySelector('input[name="username"]');
                            if (su) su.value = cardValue;
                            var sp = sendin.querySelector('input[name="password"]');
                            if (sp) sp.value = passwordValue;
                        }

                        // 4. Prefer portal's own doLogin() which hashes with MikroTicket CHAP salt
                        if (typeof doLogin === 'function') {
                            try {
                                doLogin();
                                return 'injected_dologin';
                            } catch (err) {}
                        }

                        // 5. Click submit button
                        var submitBtn = document.querySelector('form[name="login"] input[type="submit"], input[value="اتصال"], .button-submit, form[name="login"] button');
                        if (submitBtn) {
                            submitBtn.click();
                            return 'injected_click';
                        }

                        // 6. Direct form submission fallback
                        if (sendin) {
                            sendin.submit();
                            return 'injected_sendin';
                        }
                        var form = document.querySelector('form[name="login"]');
                        if (form) {
                            form.submit();
                            return 'injected_form';
                        }

                        return 'injected_form_fallback';
                    } catch(e) { return 'error: ' + e.message; }
                })();
                """.trimIndent()

                if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")
                val injectResult = evaluateJsSafely(injectionJs)
                Timber.d("[Abasha] Injection result: $injectResult")

                val checkJs = """
                (function() {
                    var html = document.documentElement.innerHTML.toLowerCase();
                    var bodyText = (document.body.innerText || '').toLowerCase();
                    var title = document.title.toLowerCase();

                    // 0. Primary Success Condition: Specific "cannot use card on two devices" notification
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

                    // 1. MikroTicket Status verified success check
                    if (title.indexOf('mikroticket status') !== -1 || html.indexOf('mikroticket status') !== -1) return 'success';
                    if (document.getElementById('mForm') || document.getElementById('infoTable') || document.getElementById('infoData')) return 'success';
                    if (html.indexOf('abasha.com/logout') !== -1) return 'success';
                    if (bodyText.indexOf('عنوان ip') !== -1 || bodyText.indexOf('وقت الاتصال') !== -1 || bodyText.indexOf('البيانات المتبقية') !== -1 || bodyText.indexOf('خطة الإنترنت') !== -1) return 'success';

                    // 2. Authorizing state
                    if (bodyText.indexOf('already authorizing') !== -1 || html.indexOf('already authorizing') !== -1) return 'authorizing';

                    // 3. Failure state (strictly excluding two-devices condition)
                    if (bodyText.indexOf('خطأ') !== -1 || bodyText.indexOf('فشل') !== -1 || bodyText.indexOf('غير صحيح') !== -1 || bodyText.indexOf('invalid') !== -1 || bodyText.indexOf('not found') !== -1 || bodyText.indexOf('منتهي') !== -1) return 'failure';

                    return 'unknown';
                })();
                """.trimIndent()

                // Condition-based polling loop
                val maxResultWaitMs = cardTestDelay.coerceAtLeast(500L)
                val checkStartTime = SystemClock.elapsedRealtime()
                var resultStr = "unknown"

                delay(150L) // Initial dispatch yield

                while (SystemClock.elapsedRealtime() - checkStartTime < maxResultWaitMs) {
                    if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")

                    // First check captured JS Alert
                    val alertText = capturedAlert()
                    if (ResultChecker.isTwoDevicesSuccess("", alertText)) {
                        resultStr = "success_two_devices"
                        break
                    }

                    resultStr = evaluateJsSafely(checkJs)
                    if (resultStr == "success" || resultStr == "success_two_devices" || resultStr == "failure") {
                        break
                    }
                    if (resultStr == "authorizing") {
                        delay(1000L)
                        resultStr = evaluateJsSafely(checkJs)
                        if (resultStr == "authorizing") {
                            if (onRequiresGlobalRelogin != null) {
                                onRequiresGlobalRelogin()
                            }
                        }
                        break
                    }
                    delay(150L)
                }

                val duration = SystemClock.elapsedRealtime() - startTime
                Timber.d("[Abasha] Check result: $resultStr (duration: ${duration}ms)")

                return@withContext when (resultStr) {
                    "success_two_devices" -> CardTestOutcome.twoDevicesSuccess(
                        message = "تم التحقق بنجاح: لا يمكن استعمال البطاقة في جهازين (البطاقة صالحة ونشطة)",
                        durationMs = duration
                    )
                    "success" -> CardTestOutcome.success(
                        message = "تم اختبار البطاقة بنجاح: تم تسجيل الدخول إلى شبكة الباشا",
                        durationMs = duration
                    )
                    "failure" -> CardTestOutcome.failure(
                        message = "فشلت عملية الاختبار: بطاقة غير صالحة أو منتهية",
                        durationMs = duration
                    )
                    else -> CardTestOutcome.timeout(
                        message = "انتهت مهلة استجابة بوابة الباشا (Timeout)",
                        durationMs = duration
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val duration = SystemClock.elapsedRealtime() - startTime
                Timber.e(e, "Error in Abasha specific test")
                return@withContext CardTestOutcome.engineError("خطأ غير متوقع: ${e.localizedMessage}", duration)
            }
        }
    }
}
