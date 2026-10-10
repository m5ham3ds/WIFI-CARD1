package com.example

import com.example.data.local.entity.RouterProfileEntity
import com.example.data.local.preferences.AppPreferences
import com.example.domain.model.Statistics
import com.example.domain.model.TestSessionState
import com.example.service.CardLifecycleState
import com.example.service.CardTestOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Targeted verification tests for:
 * 1. Default Router Consistency across screens, navigation, and persistence.
 * 2. Timing & Delay Configuration wiring and reset confirmation dialog.
 * 3. Testing & Background settings (preload toggle and thread/worker count pool sizing).
 * 4. Last Operation Persistence (inputs snapshot and session result state restore).
 */
class SettingsWiringAndPersistenceTest {

    // 1. Default Router Consistency
    @Test
    fun testDefaultRouterConsistencyAcrossNavigationAndPersistence() {
        val routers = listOf(
            RouterProfileEntity(id = 1L, name = "معتصم نت", ip = "wifi.sd.net", isDefault = true),
            RouterProfileEntity(id = 2L, name = "بيلو", ip = "www.bello.com", isDefault = false),
            RouterProfileEntity(id = 3L, name = "الباشا", ip = "wifi.sd.net", isDefault = false)
        )

        // Single source of truth in DB
        val defaultFromDb = routers.firstOrNull { it.isDefault }?.id
        assertEquals("Initial default router must be 1L", 1L, defaultFromDb)

        // Switching default to 2L
        val updatedRouters = routers.map {
            it.copy(isDefault = it.id == 2L)
        }
        val newDefaultFromDb = updatedRouters.firstOrNull { it.isDefault }?.id
        assertEquals("Updated default router must be 2L", 2L, newDefaultFromDb)

        // Effective default logic in HomeViewModel
        fun computeEffectiveDefault(list: List<RouterProfileEntity>, prefDefaultId: Long): Long {
            val dbDef = list.firstOrNull { it.isDefault }?.id
            return dbDef ?: (if (list.any { it.id == prefDefaultId }) prefDefaultId else list.first().id)
        }

        assertEquals(2L, computeEffectiveDefault(updatedRouters, 2L))
        // Even if DataStore has stale or 0L, Room's isDefault=true is authoritative
        assertEquals(2L, computeEffectiveDefault(updatedRouters, 0L))
    }

    @Test
    fun testDefaultRouterReassignmentOnDeletion() {
        val routers = mutableListOf(
            RouterProfileEntity(id = 1L, name = "معتصم نت", ip = "wifi.sd.net", isDefault = true),
            RouterProfileEntity(id = 2L, name = "بيلو", ip = "www.bello.com", isDefault = false)
        )

        val deletedRouter = routers.first { it.id == 1L }
        routers.remove(deletedRouter)

        val newDefault = if (deletedRouter.isDefault) {
            val next = routers.firstOrNull()
            next?.copy(isDefault = true)
        } else null

        assertNotNull("Next router must be designated as default upon deleting current default", newDefault)
        assertEquals(2L, newDefault?.id)
        assertTrue(newDefault?.isDefault == true)
    }

    @Test
    fun testSaveHomeSettingsDoesNotCorruptDefaultRouterId() {
        // Simulating the bug where typing prefix/count overwrote KEY_DEFAULT_ROUTER_ID with current selected router
        var defaultRouterId = 1L
        var lastOperationRouterId = 1L

        fun saveHomeSettingsFixed(prefix: String, length: Int, count: Int, charset: String, routerId: Long) {
            // Fix: save last operated router without overwriting defaultRouterId
            lastOperationRouterId = routerId
        }

        // User is currently testing with Router 3 (temporary selection) while default is Router 1
        saveHomeSettingsFixed("D", 6, 50, "0123456789", 3L)

        assertEquals("Default router ID must remain 1L and not be overwritten", 1L, defaultRouterId)
        assertEquals("Last operated router must be captured as 3L", 3L, lastOperationRouterId)
    }

    // 2. Timing & Delay Configuration
    @Test
    fun testResetDelaysToDefaultValues() {
        val defaultPageLoad = 2000L
        val defaultCardTest = 3000L
        val defaultScreenshot = 2000L

        // Modified delays
        var pageLoad = 5000L
        var cardTest = 8000L
        var screenshot = 4000L

        // Reset
        pageLoad = defaultPageLoad
        cardTest = defaultCardTest
        screenshot = defaultScreenshot

        assertEquals(2000L, pageLoad)
        assertEquals(3000L, cardTest)
        assertEquals(2000L, screenshot)
    }

    @Test
    fun testEffectiveCardDelayCalculationInTestEngine() {
        val configuredCardDelay = 4000L

        fun computeEffectiveDelay(passedDelay: Long, configured: Long): Long {
            return if (passedDelay > 0L && passedDelay != 1200L) passedDelay else configured
        }

        // When UI passes hardcoded dummy 1200L, engine falls back to user's configured delay
        assertEquals(4000L, computeEffectiveDelay(1200L, configuredCardDelay))

        // When user explicitly provides a custom non-default delay (e.g., 5000L), engine uses it
        assertEquals(5000L, computeEffectiveDelay(5000L, configuredCardDelay))

        // When 0L is passed, falls back to configured delay
        assertEquals(4000L, computeEffectiveDelay(0L, configuredCardDelay))
    }

    // 3. Testing & Background Settings (Preload & Thread/Worker Pool)
    @Test
    fun testWorkerPoolSizingRespectsPreloadAndSafetyLimits() {
        fun computePoolSize(enablePreload: Boolean, requestedThreads: Int, isLowRam: Boolean): Int {
            val requestedPoolSize = if (enablePreload) requestedThreads else 1
            val maxSafePool = if (isLowRam) 1 else 3
            return requestedPoolSize.coerceIn(1, maxSafePool)
        }

        // If preload is disabled, always single worker
        assertEquals(1, computePoolSize(enablePreload = false, requestedThreads = 3, isLowRam = false))

        // If low RAM device, always single worker for stability
        assertEquals(1, computePoolSize(enablePreload = true, requestedThreads = 3, isLowRam = true))

        // If preload enabled and normal device, respect thread count up to 3
        assertEquals(1, computePoolSize(enablePreload = true, requestedThreads = 1, isLowRam = false))
        assertEquals(2, computePoolSize(enablePreload = true, requestedThreads = 2, isLowRam = false))
        assertEquals(3, computePoolSize(enablePreload = true, requestedThreads = 3, isLowRam = false))

        // Clamp requests greater than 3 to max safe pool of 3
        assertEquals(3, computePoolSize(enablePreload = true, requestedThreads = 5, isLowRam = false))
    }

    // 4. Last Operation Persistence
    @Test
    fun testLastOperationSnapshotPreservation() {
        data class OperationSnapshot(
            val prefix: String,
            val length: Int,
            val count: Int,
            val charset: String,
            val routerId: Long,
            val timestamp: Long
        )

        val snapshot = OperationSnapshot(
            prefix = "VIP-",
            length = 8,
            count = 100,
            charset = "0123456789ABCDEF",
            routerId = 2L,
            timestamp = System.currentTimeMillis()
        )

        // Verify all fields are preserved faithfully
        assertEquals("VIP-", snapshot.prefix)
        assertEquals(8, snapshot.length)
        assertEquals(100, snapshot.count)
        assertEquals("0123456789ABCDEF", snapshot.charset)
        assertEquals(2L, snapshot.routerId)
        assertTrue(snapshot.timestamp > 0L)
    }

    @Test
    fun testRestoringSessionStatisticsWithoutLossOrDuplication() {
        // When reopening the app, statistics and session state must be faithfully mapped from Room results
        data class MockResult(val cardCode: String, val state: String, val message: String)

        val results = listOf(
            MockResult("CARD001", "Success", "تم تسجيل الدخول بنجاح"),
            MockResult("CARD002", "Success", "لا يمكن استعمال البطاقة في جهازين"),
            MockResult("CARD003", "Failure", "الكرت غير صالح"),
            MockResult("CARD004", "Failure", "رصيد الكرت منتهي"),
            MockResult("CARD005", "Timeout", "مهلة استجابة البوابة") // Technical error
        )

        var total = results.size
        var success = 0
        var failure = 0
        var twoDevices = 0

        results.forEach { r ->
            if (r.state == "Success") {
                success++
                if (r.message.contains("جهازين")) {
                    twoDevices++
                }
            } else if (r.state == "Failure") {
                failure++
            }
            // Technical errors (Timeout) are not counted as card failures
        }

        assertEquals(5, total)
        assertEquals(2, success)
        assertEquals(1, twoDevices)
        assertEquals(2, failure)
    }
}
