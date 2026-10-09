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
 * Test Strategy for Motasem Net captive portals (شبكة معتصم نت).
 * Matches portals with http://r.com/login and status pages with section username/remain and #timeLeft.
 */
object MotasemTestStrategy : RouterTestStrategy {
    override val strategyName: String = "Motasem"

    override suspend fun verifyFreshLoginPage(
        router: RouterProfileEntity,
        webView: WebView?,
        evaluateJsSafely: suspend (String) -> String
    ): Boolean {
        val checkJs = """
        (function() {
            var html = (document.documentElement.innerHTML || '').toLowerCase();
            var title = (document.title || '').toLowerCase();
            var bodyText = (document.body.innerText || '').toLowerCase();
            if (document.getElementById('timeLeft') || html.indexOf('r.com/logout') !== -1 || bodyText.indexOf('تفاصيل الأستخدام') !== -1) {
                return 'on_logout_page';
            }
            var u = document.querySelector('#username') || 
                    document.querySelector('form[name="login"] input[name="username"]') || 
                    document.querySelector('input[name="username"]') || 
                    (document.login && document.login.username);
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

                // Pre-flight check: Detects active Motasem Net session
                val preFlightJs = """
                (function() {
                    var html = document.documentElement.innerHTML.toLowerCase();
                    var bodyText = (document.body.innerText || '').toLowerCase();
                    var title = document.title.toLowerCase();

                    if (title.indexOf('شبكة معتصم نت') !== -1 && (bodyText.indexOf('تفاصيل الأستخدام') !== -1 || document.getElementById('timeLeft'))) return 'logged_in';
                    if (html.indexOf('r.com/logout') !== -1 || document.querySelector('form[action*="logout"]')) return 'logged_in';
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

                        delay(1500)
                        if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")
                        wv.loadUrl(url)
                        delay(1000)
                    }
                }

                // Wait for login form to be ready
                val checkReadyJs = """
                (function() {
                    if (document.readyState !== 'complete' && document.readyState !== 'interactive') return 'not_ready';
                    var u = document.querySelector('#username') || 
                            document.querySelector('form[name="login"] input[name="username"]') || 
                            document.querySelector('input[name="username"]') || 
                            (document.login && document.login.username);
                    return u ? 'ready' : 'not_ready';
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

                val safeCard = JSONObject.quote(card).removeSurrounding("\"").replace("'", "\\'")
                val safePassword = if (router.passwordEnabled) JSONObject.quote(router.getEffectivePassword()).removeSurrounding("\"").replace("'", "\\'") else ""

                val injectionJs = """
                (function() {
                    try {
                        function triggerEvents(el) {
                            if(!el) return;
                            try {
                                var ev1 = document.createEvent('Event'); ev1.initEvent('input', true, true); el.dispatchEvent(ev1);
                                var ev2 = document.createEvent('Event'); ev2.initEvent('change', true, true); el.dispatchEvent(ev2);
                            } catch(e) {}
                        }

                        var cardValue = '$safeCard';
                        var passwordValue = '$safePassword';

                        // 1. Fill visible username input
                        if (document.login && document.login.username) {
                            document.login.username.value = cardValue;
                            triggerEvents(document.login.username);
                        } else {
                            var u = document.querySelector('form[name="login"] input[name="username"]') || 
                                    document.querySelector('#username') || 
                                    document.querySelector('input[name="username"]:not([type="hidden"])');
                            if (u) {
                                u.value = cardValue;
                                triggerEvents(u);
                            }
                        }

                        // 2. Fill password input if needed
                        if (document.login && document.login.password) {
                            document.login.password.value = passwordValue;
                            triggerEvents(document.login.password);
                        } else {
                            var pass = document.querySelector('form[name="login"] input[name="password"]') || 
                                       document.querySelector('input[name="password"]');
                            if (pass) {
                                pass.value = passwordValue;
                                triggerEvents(pass);
                            }
                        }

                        // 3. Prefer portal's own doLogin() which hashes with Motasem CHAP salt
                        if (typeof doLogin === 'function') {
                            try {
                                doLogin();
                                return 'injected_dologin';
                            } catch (err) {}
                        }

                        // 4. Set and submit hidden sendin form directly
                        var sendinForm = document.querySelector('form[name="sendin"]');
                        if (sendinForm) {
                            var su = sendinForm.querySelector('input[name="username"]');
                            if (su) su.value = cardValue;
                            var sp = sendinForm.querySelector('input[name="password"]');
                            if (sp) sp.value = passwordValue;
                            sendinForm.submit();
                            return 'injected_sendin_fallback';
                        }

                        // 5. Click submit button
                        var submitBtn = document.querySelector('form[name="login"] button[type=submit], .submit button, form[name="login"] input[type=submit]');
                        if (submitBtn) {
                            submitBtn.click();
                            return 'injected_click_processed';
                        }

                        // 6. Direct form submission fallback
                        var form = document.querySelector('form[name="login"]');
                        if (form) {
                            form.submit();
                            return 'injected_form_fallback';
                        }

                        return 'injected_no_submit_found';
                    } catch(e) { return 'error: ' + e.message; }
                })();
                """.trimIndent()

                if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")
                val injectResult = evaluateJsSafely(injectionJs)
                Timber.d("[Motasem] Injection result: $injectResult")

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

                    // Motasem Net Status indicators
                    if (document.getElementById('timeLeft') && document.querySelector('.section.username')) return 'success';
                    if (bodyText.indexOf('تفاصيل الأستخدام') !== -1 && bodyText.indexOf('الوقت المتبقي') !== -1) return 'success';
                    if (document.querySelector('.section.remain') && html.indexOf('readablizebytes') !== -1) return 'success';
                    if (html.indexOf('r.com/logout') !== -1) return 'success';
                    if (title.indexOf('شبكة معتصم نت') !== -1 && bodyText.indexOf('أسم المستخدم') !== -1) return 'success';

                    // Authorizing state
                    if (bodyText.indexOf('already authorizing') !== -1 || html.indexOf('already authorizing') !== -1) return 'authorizing';

                    // Failure state
                    if (bodyText.indexOf('خطأ') !== -1 || bodyText.indexOf('فشل') !== -1 || bodyText.indexOf('غير صحيح') !== -1 || bodyText.indexOf('invalid') !== -1 || bodyText.indexOf('not found') !== -1 || bodyText.indexOf('منتهي') !== -1 || bodyText.indexOf('نفذ الرصيد') !== -1 || bodyText.indexOf('incorrect') !== -1 || bodyText.indexOf('expired') !== -1 || (bodyText.indexOf('لا يمكن') !== -1 && bodyText.indexOf('جهازين') === -1)) return 'failure';

                    return 'unknown';
                })();
                """.trimIndent()

                val maxResultWaitMs = cardTestDelay.coerceAtLeast(500L)
                val checkStartTime = SystemClock.elapsedRealtime()
                var resultStr = "unknown"

                delay(150L) // Initial short yield for form dispatch

                while (SystemClock.elapsedRealtime() - checkStartTime < maxResultWaitMs) {
                    if (isBlockedBySuccess()) return@withContext CardTestOutcome.failure("مقاطعة بنجاح بطاقة أخرى")

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
                Timber.d("[Motasem] Check result: $resultStr (duration: ${duration}ms)")

                return@withContext when (resultStr) {
                    "success_two_devices" -> CardTestOutcome.twoDevicesSuccess(
                        message = "تم التحقق بنجاح: لا يمكن استعمال البطاقة في جهازين (البطاقة صالحة ونشطة)",
                        durationMs = duration
                    )
                    "success" -> CardTestOutcome.success(
                        message = "تم اختبار البطاقة بنجاح: تم تسجيل الدخول إلى شبكة معتصم نت",
                        durationMs = duration
                    )
                    "failure" -> CardTestOutcome.failure(
                        message = "فشلت عملية الاختبار: بطاقة غير صالحة أو منتهية",
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
