package com.example

import com.example.data.local.entity.RouterProfileEntity
import com.example.service.*
import org.junit.Assert.*
import org.junit.Test

class HotspotStrategyTest {

    private val albashaLoginPageHtml = """
        <!DOCTYPE html><html lang="en"><head>
        <title>Internet hotspot - Log in</title>
        </head><body>
        <form name="sendin" action="http://www.Abasha.com/login" method="post" style="display:none">
            <input type="hidden" name="username">
            <input type="hidden" name="password">
            <input type="hidden" name="dst" value="http://10.0.0.1">
            <input type="hidden" name="popup" value="true">
        </form>
        <div class="form-container">
            <ul class="tabs">
                <li class="tab active" data-tab="pin"><a href="#login">🔑</a></li>
                <li class="tab" data-tab="user"><a href="#signup">👤</a></li>
            </ul>
            <form name="login" action="http://www.Abasha.com/login" method="post" onsubmit="return doLogin()">
                <p class="info">يرجى تسجيل الدخول لاستخدام خدمه الانترنت</p>
                <input name="username" class="input-text" type="text" value="">
                <input name="password" class="input-text" type="password">
                <input type="submit" value="اتصال" class="button-submit">
            </form>
            <p>بدعم من MikroTicket</p>
        </div>
        </body></html>
    """.trimIndent()

    private val albashaStatusPageHtml = """
        <!DOCTYPE html><html><head>
        <title>MikroTicket Status</title>
        <script>
            function openLogout() {
                open('http://www.Abasha.com/logout', 'hotspot_logout');
                return false;
            }
        </script>
        </head><body>
        <div class="wrap status-card">
            <button class="btn-main" onclick="document.getElementById('mForm').submit();"></button>
            <h1>✋, testcard123!</h1>
            <form id="mForm" action="http://www.Abasha.com/logout" name="logout" onsubmit="return openLogout()">
                <table id="infoTable">
                    <tbody>
                    <tr><td>عنوان IP</td><td>10.0.0.229</td></tr>
                    <tr><td>⬇️ تنزيل</td><td>60 B</td></tr>
                    <tr><td>⬆️ رفع</td><td>681 B</td></tr>
                    <tr><td>⏱️ وقت الاتصال</td><td>0s</td></tr>
                    </tbody>
                </table>
                <table id="infoData">
                    <tr><td>🏷️ خطة الإنترنت</td><td>1 Hour Plan</td></tr>
                    <tr><td>📉 البيانات المتبقية</td><td>500 MB</td></tr>
                </table>
            </form>
        </div>
        </body></html>
    """.trimIndent()

    private val belloLoginPageHtml = """
        <!DOCTYPE html><html lang="en"><head>
        <title>تسجيل الدخول</title>
        </head><body>
        <form name="sendin" action="http://www.bello.com/login" method="post"></form>
        <div class="form">
            <form name="login" action="http://www.bello.com/login" method="post">
                <div class="input-field">
                    <input id="uname" type="text" name="username" required="" size="42">
                    <label for="username">أدخل كرتك هنا</label>
                </div>
                <div class="submit">
                    <button type="submit">تسجيل الدخول</button>
                </div>
            </form>
            <footer class="copyright2">MHTRF SYRIA © 2022</footer>
        </div>
        </body></html>
    """.trimIndent()

    private val belloStatusPageHtml = """
        <!DOCTYPE html><html><head>
        <title>معلومات الإشتراك</title>
        </head><body>
        <form action="http://www.bello.com/logout" name="logout">
        <div class="box">
            <p class="details">تفاصيل الحساب</p>
            <div id="battery" class="battery"></div>
            <div class="info blue">
                <p class="header">اسم المستخدم</p>
                <p class="valuee" id="card">9041****</p>
            </div>
            <div class="info red">
                <p class="header">المتبقي من الرصيد</p>
                <p class="valuee">1.5 GB</p>
            </div>
            <div class="info green">
                <p class="header">المتبقي من الوقت</p>
                <p id="timeLeft" class="valuee">3d 12h</p>
            </div>
            <div class="submit">
                <button type="submit">تسجيل الخروج</button>
            </div>
        </div>
        </form>
        </body></html>
    """.trimIndent()

    private val motasemLoginPageHtml = """
        <!DOCTYPE html><html><head>
        <title>شبكة معتصم نت</title>
        </head><body>
        <form name="sendin" action="http://r.com/login" method="post">
            <input type="hidden" name="username">
            <input type="hidden" name="password">
        </form>
        <div class="form-box">
            <form name="login" action="http://r.com/login" method="post" onsubmit="return doLogin()">
                <div class="form-title"><p>ادخل الرمز</p></div>
                <div class="input-field">
                    <input name="username" value="" id="username" placeholder="اسم المستخدم">
                    <input style="width:0px" name="password" type="password">
                </div>
                <div class="submit">
                    <button type="submit">تسجيل الدخول</button>
                </div>
            </form>
        </div>
        </body></html>
    """.trimIndent()

    private val motasemStatusPageHtml = """
        <!DOCTYPE html><html><head>
        <title>شبكة معتصم نت</title>
        </head><body>
        <div class="box">
            <form action="http://r.com/logout" name="logout" onsubmit="return openLogout()">
                <div class="header"><h4>تفاصيل الأستخدام</h4></div>
                <div class="section username"><h4>أسم المستخدم:</h4><h4>904123</h4></div>
                <div class="section card">
                    <h4>الوقت المتبقي:</h4>
                    <h4 id="timeLeft">5 يوم , 9 ساعة , 43 دقيقة</h4>
                </div>
                <div class="section remain">
                    <h4>الرصيد المتبقي:</h4>
                    <h4>3.64 جيجابايت</h4>
                </div>
                <div class="submit"><button type="submit">تسجيل الخروج</button></div>
            </form>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun testAlBashaPortalDetectionAndResultChecking() {
        val router = RouterProfileEntity(
            name = "شبكة الباشا",
            ip = "wifi.sd.net",
            strategyId = "abasha",
            successIndicator = "MikroTicket Status",
            failureIndicator = "خطأ"
        )

        // Login page should NOT be evaluated as success or logged in
        assertFalse(ResultChecker.isSuccess(albashaLoginPageHtml, "يرجى تسجيل الدخول", router))
        assertFalse(ResultChecker.isLoggedIn(albashaLoginPageHtml, "يرجى تسجيل الدخول", router))

        // Status page must be evaluated as success and logged in
        assertTrue(ResultChecker.isSuccess(albashaStatusPageHtml, "عنوان IP وقت الاتصال البيانات المتبقية", router))
        assertTrue(ResultChecker.isLoggedIn(albashaStatusPageHtml, "عنوان IP وقت الاتصال", router))

        // Factory routing via explicit strategyId and entity
        val strategyFromId = RouterStrategyFactory.getStrategy(router.strategyId)
        val strategyFromEntity = RouterStrategyFactory.getStrategy(router)
        assertEquals(AbashaTestStrategy.strategyName, strategyFromId.strategyName)
        assertTrue(strategyFromId is AbashaTestStrategy)
        assertTrue(strategyFromEntity is AbashaTestStrategy)
    }

    @Test
    fun testBelloPortalDetectionAndResultChecking() {
        val router = RouterProfileEntity(
            name = "بيلو",
            ip = "www.bello.com",
            strategyId = "bello",
            successIndicator = "id=\"card\"",
            failureIndicator = "خطأ"
        )

        // Login page should NOT be success
        assertFalse(ResultChecker.isSuccess(belloLoginPageHtml, "أدخل كرتك هنا", router))

        // Status page must be success
        assertTrue(ResultChecker.isSuccess(belloStatusPageHtml, "تفاصيل الحساب المتبقي من الرصيد المتبقي من الوقت", router))

        // Extract remaining data and time
        val data = ResultChecker.extractRemainingData(belloStatusPageHtml, "المتبقي من الرصيد 1.5 GB")
        assertEquals("1.5 GB", data)

        // Factory routing
        val strategyFromId = RouterStrategyFactory.getStrategy(router.strategyId)
        val strategyFromEntity = RouterStrategyFactory.getStrategy(router)
        assertEquals(BelloTestStrategy.strategyName, strategyFromId.strategyName)
        assertTrue(strategyFromId is BelloTestStrategy)
        assertTrue(strategyFromEntity is BelloTestStrategy)
    }

    @Test
    fun testMotasemPortalDetectionAndResultChecking() {
        val router = RouterProfileEntity(
            name = "شبكة معتصم نت",
            ip = "wifi.sd.net",
            strategyId = "motasem",
            successIndicator = "تفاصيل الأستخدام",
            failureIndicator = "خطأ"
        )

        // Login page should NOT be success
        assertFalse(ResultChecker.isSuccess(motasemLoginPageHtml, "ادخل الرمز اسم المستخدم", router))

        // Status page must be success
        assertTrue(ResultChecker.isSuccess(motasemStatusPageHtml, "تفاصيل الأستخدام الوقت المتبقي الرصيد المتبقي 3.64 جيجابايت", router))

        // Time extraction
        val time = ResultChecker.extractRemainingTime(motasemStatusPageHtml, "الوقت المتبقي: 5 يوم , 9 ساعة , 43 دقيقة")
        assertNotNull(time)
        assertTrue(time!!.contains("5 يوم"))

        // Factory routing
        val strategyFromId = RouterStrategyFactory.getStrategy(router.strategyId)
        val strategyFromEntity = RouterStrategyFactory.getStrategy(router)
        assertEquals(MotasemTestStrategy.strategyName, strategyFromId.strategyName)
        assertTrue(strategyFromId is MotasemTestStrategy)
        assertTrue(strategyFromEntity is MotasemTestStrategy)
    }

    @Test
    fun testFailureAndAuthorizingDetection() {
        val router = RouterProfileEntity(name = "Test Portal", ip = "192.168.1.1", failureIndicator = "error")

        val failurePage = "<html><body><p class='error'>خطأ: الكرت غير صحيح أو منتهي الصلاحية</p></body></html>"
        assertTrue(ResultChecker.isFailure(failurePage, "خطأ: الكرت غير صحيح أو منتهي الصلاحية", router))
        assertFalse(ResultChecker.isSuccess(failurePage, "خطأ", router))

        val authPage = "<html><body><p>already authorizing, please wait...</p></body></html>"
        assertTrue(ResultChecker.isAuthorizing(authPage, "already authorizing"))
        assertFalse(ResultChecker.isSuccess(authPage, "already authorizing", router))
    }

    @Test
    fun testStrategyFactoryRoutingByIp() {
        // Even if user names the profile differently, IP routes correctly for unique legacy hosts:
        val routerWithAbashaIp = RouterProfileEntity(name = "My Custom Network", ip = "www.abasha.com", strategyId = "generic")
        assertEquals(AbashaTestStrategy.strategyName, RouterStrategyFactory.getStrategy(routerWithAbashaIp).strategyName)

        val routerWithBelloIp = RouterProfileEntity(name = "Custom Portal", ip = "www.bello.com", strategyId = "generic")
        assertEquals(BelloTestStrategy.strategyName, RouterStrategyFactory.getStrategy(routerWithBelloIp).strategyName)

        val routerWithMotasemIp = RouterProfileEntity(name = "Neighborhood Wifi", ip = "r.com", strategyId = "generic")
        assertEquals(MotasemTestStrategy.strategyName, RouterStrategyFactory.getStrategy(routerWithMotasemIp).strategyName)

        val genericRouter = RouterProfileEntity(name = "TPLink Gateway", ip = "192.168.0.1", strategyId = "generic")
        assertEquals(GenericTestStrategy.strategyName, RouterStrategyFactory.getStrategy(genericRouter).strategyName)
    }

    // ==========================================
    // PHASE 4.1 MANDATORY VERIFICATION TESTS
    // ==========================================

    @Test
    fun test1_AlBashaStrategyIdentity() {
        val router = RouterProfileEntity(
            name = "شبكة الباشا",
            ip = "wifi.sd.net",
            strategyId = "abasha"
        )
        val strategy = RouterStrategyFactory.getStrategy(router.strategyId)
        assertEquals(AbashaTestStrategy.strategyName, strategy.strategyName)
        assertTrue("Factory must return AbashaTestStrategy for strategyId=abasha", strategy is AbashaTestStrategy)
        assertTrue(RouterStrategyFactory.getStrategy(router) is AbashaTestStrategy)
    }

    @Test
    fun test2_MotasemStrategyIdentity() {
        val router = RouterProfileEntity(
            name = "شبكة معتصم نت",
            ip = "wifi.sd.net",
            strategyId = "motasem"
        )
        val strategy = RouterStrategyFactory.getStrategy(router.strategyId)
        assertEquals(MotasemTestStrategy.strategyName, strategy.strategyName)
        assertTrue("Factory must return MotasemTestStrategy for strategyId=motasem", strategy is MotasemTestStrategy)
        assertTrue(RouterStrategyFactory.getStrategy(router) is MotasemTestStrategy)
    }

    @Test
    fun test3_BelloStrategyIdentity() {
        val router = RouterProfileEntity(
            name = "بيلو",
            ip = "www.bello.com",
            strategyId = "bello"
        )
        val strategy = RouterStrategyFactory.getStrategy(router.strategyId)
        assertEquals(BelloTestStrategy.strategyName, strategy.strategyName)
        assertTrue("Factory must return BelloTestStrategy for strategyId=bello", strategy is BelloTestStrategy)
        assertTrue(RouterStrategyFactory.getStrategy(router) is BelloTestStrategy)
    }

    @Test
    fun test4_SharedHostMustNotCollide() {
        // MANDATORY TEST: Both profiles share wifi.sd.net as their runtime host
        val albashaRouter = RouterProfileEntity(
            name = "شبكة الباشا",
            ip = "wifi.sd.net",
            strategyId = "abasha"
        )
        val motasemRouter = RouterProfileEntity(
            name = "شبكة معتصم نت",
            ip = "wifi.sd.net",
            strategyId = "motasem"
        )

        assertEquals("Both routers MUST share runtime host wifi.sd.net", albashaRouter.ip, motasemRouter.ip)
        assertEquals("wifi.sd.net", albashaRouter.ip)

        val albashaStrategy = RouterStrategyFactory.getStrategy(albashaRouter)
        val motasemStrategy = RouterStrategyFactory.getStrategy(motasemRouter)

        assertTrue(albashaStrategy is AbashaTestStrategy)
        assertTrue(motasemStrategy is MotasemTestStrategy)
        assertNotEquals("Strategies sharing wifi.sd.net MUST NOT collide", albashaStrategy.strategyName, motasemStrategy.strategyName)
        assertNotSame(albashaStrategy, motasemStrategy)
    }

    @Test
    fun test5_GenericStrategy() {
        val router = RouterProfileEntity(
            name = "Generic Custom Router",
            ip = "192.168.1.1",
            strategyId = "generic"
        )
        val strategy = RouterStrategyFactory.getStrategy(router.strategyId)
        assertEquals(GenericTestStrategy.strategyName, strategy.strategyName)
        assertTrue("Router with strategyId=generic must return GenericTestStrategy", strategy is GenericTestStrategy)
        assertTrue(RouterStrategyFactory.getStrategy(router) is GenericTestStrategy)

        // Shared host wifi.sd.net with generic strategyId MUST NOT resolve to Albasha or Motasem!
        val sharedHostGeneric = RouterProfileEntity(
            name = "Unrelated Network",
            ip = "wifi.sd.net",
            strategyId = "generic"
        )
        assertTrue(
            "Shared host wifi.sd.net must NEVER fall back to Albasha/Motasem by host alone",
            RouterStrategyFactory.getStrategy(sharedHostGeneric) is GenericTestStrategy
        )
    }

    @Test
    fun test6_AuthenticationContract() {
        val rawPassword = "P@ssw0rd123Secure!"
        val encryptedPassword = com.example.util.SecurityUtils.encryptPasswordAtRest(rawPassword)

        // When passwordEnabled = false: does NOT attempt password submission
        val profilePasswordDisabled = RouterProfileEntity(
            name = "شبكة الباشا",
            ip = "wifi.sd.net",
            strategyId = "abasha",
            password = encryptedPassword,
            passwordEnabled = false
        )
        assertFalse(profilePasswordDisabled.passwordEnabled)
        assertEquals("Effective password must be empty when passwordEnabled is false", "", profilePasswordDisabled.getEffectivePassword())

        // When passwordEnabled = true: requires and submits decrypted password
        val profilePasswordEnabled = RouterProfileEntity(
            name = "شبكة الباشا",
            ip = "wifi.sd.net",
            strategyId = "abasha",
            password = encryptedPassword,
            passwordEnabled = true
        )
        assertTrue(profilePasswordEnabled.passwordEnabled)
        assertEquals("Effective password must be decrypted when passwordEnabled is true", rawPassword, profilePasswordEnabled.getEffectivePassword())
    }
}
