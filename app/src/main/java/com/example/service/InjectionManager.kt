package com.example.service

import org.json.JSONObject
import timber.log.Timber

object InjectionManager {

    fun quote(str: String): String {
        return try {
            JSONObject.quote(str)
        } catch (_: Throwable) {
            "\"" + str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\""
        }
    }

    fun buildInjectionJs(
        card: String,
        usernameSel: String,
        passwordSel: String,
        submitSel: String,
        password: String = ""
    ): String {
        val cardJson = quote(card)
        val uSelJson = quote(usernameSel)
        val pSelJson = quote(passwordSel)
        val sSelJson = quote(submitSel)
        val pwdJson = quote(password)

        val js = """
        (function() {
            try {
                var uSel = $uSelJson;
                var pSel = $pSelJson;
                var sSel = $sSelJson;
                var cardVal = $cardJson;
                var pwdVal = $pwdJson;

                function triggerEvents(el) {
                    if(!el) return;
                    try {
                        var ev1 = document.createEvent('Event'); ev1.initEvent('input', true, true); el.dispatchEvent(ev1);
                        var ev2 = document.createEvent('Event'); ev2.initEvent('change', true, true); el.dispatchEvent(ev2);
                    } catch(e) {}
                }

                var uFound = false, pFound = false, sFound = null;

                // Try to fill all inputs that match username / card
                var uNodes = uSel ? document.querySelectorAll(uSel) : document.querySelectorAll('#username, #uname, input[type="text"]:not([type="hidden"]), input[name="username"]');
                for(var i=0; i<uNodes.length; i++) {
                    uNodes[i].value = cardVal;
                    triggerEvents(uNodes[i]);
                    uFound = true;
                }

                // Try to fill all inputs that match password
                var pNodes = pSel ? document.querySelectorAll(pSel) : document.querySelectorAll('#password, input[type="password"]:not([type="hidden"]), input[name="password"]');
                for(var i=0; i<pNodes.length; i++) {
                    pNodes[i].value = pwdVal;
                    triggerEvents(pNodes[i]);
                    pFound = true;
                }

                // Fill hidden sendin form if present
                var sendin = document.querySelector('form[name="sendin"]');
                if (sendin) {
                    var su = sendin.querySelector('input[name="username"]');
                    if (su) su.value = cardVal;
                    var sp = sendin.querySelector('input[name="password"]');
                    if (sp) sp.value = pwdVal;
                }

                // Portal-native CHAP MD5 submission
                if (typeof doLogin === 'function') {
                    try {
                        doLogin();
                        return 'injected_dologin';
                    } catch(err) {}
                }

                var s = sSel ? document.querySelector(sSel) : null;
                if (!s) s = document.querySelector('button[type="submit"], input[type="submit"], form input[type="submit"], .submit button, .button-submit, .btn-main');

                if (uFound) {
                    if (s) {
                        s.click();
                        return 'injected_clicked';
                    } else if (sendin) {
                        sendin.submit();
                        return 'injected_sendin';
                    } else if (uNodes.length > 0 && uNodes[0].form) {
                        try {
                            var evt = document.createEvent('Event');
                            evt.initEvent('submit', true, true);
                            if (uNodes[0].form.dispatchEvent(evt)) {
                                uNodes[0].form.submit();
                            }
                        } catch(e) { uNodes[0].form.submit(); }
                        return 'injected_form';
                    }
                    return 'injected';
                }

                return 'selectors_not_found';
            } catch(e) { return 'error:' + e.message; }
        })();
        """.trimIndent()
        Timber.v("Constructed injection JS for selector: $usernameSel (card len: ${card.length})")
        return js
    }

    fun buildLogoutJs(logoutSel: String): String {
        val lSelJson = quote(logoutSel)
        return """
        (function() {
            try {
                var lSel = $lSelJson;
                var btn = lSel ? document.querySelector(lSel) : null;
                if (btn) {
                    if (btn.tagName && btn.tagName.toLowerCase() === 'form') {
                        btn.submit();
                        return 'form_submitted';
                    }
                    btn.click();
                    return 'clicked';
                }

                var mForm = document.getElementById('mForm');
                if (mForm) { mForm.submit(); return 'mForm'; }
                var f2 = document.querySelector('form[action*="logout"], form[name="logout"]');
                if (f2) { f2.submit(); return 'logout_form'; }

                var links = document.querySelectorAll('a, button, input[type="button"], input[type="submit"]');
                for (var i = 0; i < links.length; i++) {
                    var text = (links[i].textContent || '').toLowerCase();
                    var value = (links[i].value || '').toLowerCase();
                    if (text.indexOf('logout') !== -1 || text.indexOf('تسجيل الخروج') !== -1 || text.indexOf('خروج') !== -1 || value.indexOf('logout') !== -1 || value.indexOf('تسجيل الخروج') !== -1) {
                        links[i].click();
                        return 'clicked_fallback';
                    }
                }
                if (typeof openLogout === 'function') { openLogout(); return 'openLogout'; }
                return 'not_found';
            } catch(e) { return 'error:' + e.message; }
        })();
        """.trimIndent()
    }

    fun buildCheckResultJs(successInd: String, failureInd: String, loginSel: String, logoutSel: String): String {
        val successIndJson = quote(successInd)
        val failureIndJson = quote(failureInd)

        return """
        (function() {
            try {
                var html = document.documentElement.innerHTML.toLowerCase();
                var bodyText = (document.body.innerText || '').toLowerCase();
                var title = document.title.toLowerCase();
                var safeSuccess = $successIndJson.toLowerCase();
                var safeFailure = $failureIndJson.toLowerCase();

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

                // 1. Explicit Failure Indicators
                if (safeFailure !== '' && html.indexOf(safeFailure) !== -1) return 'failure';
                if (bodyText.indexOf('already authorizing') !== -1 || html.indexOf('already authorizing') !== -1) {
                    return 'authorizing';
                }
                if (bodyText.indexOf('خطأ') !== -1 || bodyText.indexOf('فشل') !== -1 || bodyText.indexOf('غير صحيح') !== -1 || bodyText.indexOf('invalid') !== -1 || bodyText.indexOf('incorrect') !== -1 || bodyText.indexOf('not found') !== -1 || bodyText.indexOf('منتهي') !== -1) {
                    return 'failure';
                }

                // 2. Explicit Success Indicators
                if (safeSuccess !== '' && safeSuccess !== 'null' && html.indexOf(safeSuccess) !== -1) {
                   return 'success';
                }

                // Router-specific verified success pages from real captive portals
                if (title.indexOf('mikroticket status') !== -1 || html.indexOf('mikroticket status') !== -1) return 'success';
                if (document.getElementById('mForm') || document.getElementById('infoTable')) return 'success';
                if (document.getElementById('card') || document.getElementById('battery')) return 'success';
                if (document.getElementById('timeLeft') && (document.querySelector('.section.username') || bodyText.indexOf('المتبقي من الوقت') !== -1)) return 'success';
                if (bodyText.indexOf('تفاصيل الأستخدام') !== -1 && bodyText.indexOf('الوقت المتبقي') !== -1) return 'success';

                // Fallback strict success checks
                var hasLogout = (html.indexOf('logout') !== -1 || html.indexOf('تسجيل الخروج') !== -1 || html.indexOf('خروج') !== -1);
                var hasSuccessText = (bodyText.indexOf('الوقت المتبقي') !== -1 || bodyText.indexOf('الميغبايت') !== -1 || bodyText.indexOf('الرصيد المتبقي') !== -1 || bodyText.indexOf('المتبقي') !== -1 || bodyText.indexOf('عنوان ip') !== -1 || bodyText.indexOf('وقت الاتصال') !== -1);

                if (hasLogout && hasSuccessText) {
                    return 'success';
                }

                // 3. Intermediate logic check (Redirect page)
                if (html.indexOf('سيتم الآن تحويلك') !== -1 || html.indexOf('redirect') !== -1 || html.indexOf('please wait') !== -1) {
                    return 'redirecting';
                }

                return 'unknown';
            } catch(e) { return 'error:' + e.message; }
        })();
        """.trimIndent()
    }
}
