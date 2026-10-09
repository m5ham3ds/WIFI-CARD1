package com.example.service

// Synced to trigger GitHub changes

// Synced project files for native translation handling
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.http.SslError
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.JsResult
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.local.entity.RouterProfileEntity
import com.example.data.local.entity.TestResultEntity
import com.example.data.local.preferences.AppPreferences
import com.example.domain.model.LogLevel
import com.example.domain.model.ResultCategory
import com.example.domain.model.ResultSubReason
import com.example.domain.repository.IRouterRepository
import com.example.domain.repository.ISessionRepository
import com.example.domain.repository.ITestResultRepository
import com.example.util.SecurityUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import android.app.ActivityManager
import com.example.service.BelloTestStrategy
import com.example.service.AbashaTestStrategy
import com.example.service.MotasemTestStrategy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class TestService : Service(), KoinComponent {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val ACTION_RETRY_LOAD = "ACTION_RETRY_LOAD"

        const val EXTRA_ROUTER_ID = "EXTRA_ROUTER_ID"
        const val EXTRA_CARD_LIST = "EXTRA_CARD_LIST"
        const val EXTRA_DELAY_MS = "EXTRA_DELAY_MS"

        private val _serviceState = MutableStateFlow(ServiceState())
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private val routerRepository: IRouterRepository by inject()
    private val sessionRepository: ISessionRepository by inject()
    private val testResultRepository: ITestResultRepository by inject()
    private val appPreferences: AppPreferences by inject()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val webViewPool = mutableListOf<WebView>()
    private data class NavigationToken(
        val generation: Long,
        val deferred: CompletableDeferred<Unit>,
        val targetUrl: String
    )
    private val activeNavigations = java.util.concurrent.ConcurrentHashMap<WebView, NavigationToken>()
    private val navigationGenerationSeq = AtomicLong(1000L)
    private var currentWebViewIndex = 0

    private val activeWebView: WebView?
        get() = webViewPool.getOrNull(currentWebViewIndex)

    private var testJob: Job? = null
    private var screenshotJob: Job? = null
    private val binder = ServiceBinder(this)
    private lateinit var notificationHelper: NotificationHelper

    private var retryDeferred: CompletableDeferred<Boolean>? = null
    private var currentTargetRouterIp: String? = null
    private val lastNotificationUpdateTime = AtomicLong(0L)
    private var currentSessionId: Long? = null
    private val capturedAlerts = java.util.concurrent.ConcurrentHashMap<WebView, Pair<Long, String>>()
    private val finalizedAttempts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val stopRequestedDueToSuccess = java.util.concurrent.atomic.AtomicBoolean(false)

    private data class CardAttempt(
        val sessionId: Long,
        val cardIndex: Int,
        val cardCode: String,
        val workerId: Int,
        val attemptSequence: Int,
        val generationToken: Long
    )

    private fun maskCard(card: String): String =
        if (card.length > 4) card.take(2) + "***" + card.takeLast(2) else "***"

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        notificationHelper = NotificationHelper(this)
    }

    private fun createWebViewInstance(): WebView {
        return WebView(applicationContext).apply {
            val dm: DisplayMetrics = resources.displayMetrics
            layout(0, 0, dm.widthPixels, dm.heightPixels)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE
                userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
                // Security hardening: restrict filesystem & content access
                allowFileAccess = false
                allowContentAccess = false
            }
            webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    if (view != null && message != null) {
                        val gen = activeNavigations[view]?.generation ?: navigationGenerationSeq.get()
                        capturedAlerts[view] = Pair(gen, message)
                        Timber.d("Captured onJsAlert (gen $gen): $message on URL: $url")
                    }
                    result?.confirm()
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Timber.d("WebView page loaded: $url")
                    
                    // Auto-click reload button if present on page load
                    val autoReloadJs = """
                        (function() {
                            var buttons = document.querySelectorAll('button, a, input[type="button"]');
                            for (var i = 0; i < buttons.length; i++) {
                                var text = (buttons[i].innerText || buttons[i].value || '').toLowerCase();
                                if (text.indexOf('إعادة تحميل') !== -1 || text.indexOf('retry') !== -1 || text.indexOf('reload') !== -1 || text.indexOf('تحديث') !== -1) {
                                    buttons[i].click();
                                    return true;
                                }
                            }
                            return false;
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(autoReloadJs) { result ->
                        if (result == "true") {
                            Timber.d("Auto-clicked reload button")
                        }
                    }

                    if (view != null) {
                        val nav = activeNavigations[view]
                        if (nav != null && nav.deferred.isActive) {
                            nav.deferred.complete(Unit)
                        }
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?, handler: SslErrorHandler?, error: SslError?
                ) {
                    val failingUrl = error?.url ?: view?.url ?: ""
                    val isAllowedRouterHost = SecurityUtils.isPrivateNetworkOrRouterHost(failingUrl, currentTargetRouterIp)
                    if (isAllowedRouterHost) {
                        Timber.w("Proceeding with self-signed SSL certificate for authorized router endpoint: primaryError=${error?.primaryError} host=$failingUrl")
                        handler?.proceed()
                    } else {
                        Timber.e("REJECTING insecure SSL certificate for external/unverified host: primaryError=${error?.primaryError} host=$failingUrl")
                        handler?.cancel()
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Timber.e("WebView error: ${error?.description} code: ${error?.errorCode}")
                    if (request?.isForMainFrame == true && view != null) {
                        if (error?.errorCode == WebViewClient.ERROR_UNKNOWN || error?.description?.contains("net::ERR_ABORTED", ignoreCase = true) == true) {
                            Timber.d("Ignoring ERR_ABORTED for main frame")
                            return
                        }
                        val nav = activeNavigations[view]
                        if (nav != null && nav.deferred.isActive) {
                            val errMsg = error?.description?.toString() ?: "فشل تحميل الصفحة"
                            nav.deferred.completeExceptionally(Exception(errMsg))
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return START_NOT_STICKY
        }
        val action = intent.action
        Timber.d("onStartCommand action: $action")

        if (action == ACTION_START) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    startForeground(
                        NotificationHelper.NOTIFICATION_ID,
                        notificationHelper.buildRunningNotification(_serviceState.value, forceStandard = false),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start foreground with custom view, falling back to standard view")
                    try {
                        startForeground(
                            NotificationHelper.NOTIFICATION_ID,
                            notificationHelper.buildRunningNotification(_serviceState.value, forceStandard = true),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } catch (firstLevelEx: Exception) {
                        Timber.e(firstLevelEx, "Critical: Start foreground failed even with standard layout")
                    }
                }
            } else {
                try {
                    startForeground(
                        NotificationHelper.NOTIFICATION_ID,
                        notificationHelper.buildRunningNotification(_serviceState.value, forceStandard = false)
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start foreground with custom view, falling back to standard view")
                    try {
                        startForeground(
                            NotificationHelper.NOTIFICATION_ID,
                            notificationHelper.buildRunningNotification(_serviceState.value, forceStandard = true)
                        )
                    } catch (secondLevelEx: Exception) {
                        Timber.e(secondLevelEx, "Critical: Start foreground failed even with standard layout on older SDK")
                    }
                }
            }
        }

        when (action) {
            ACTION_START -> {
                val routerId = intent.getLongExtra(EXTRA_ROUTER_ID, -1L)
                val cardList = intent.getStringArrayListExtra(EXTRA_CARD_LIST) ?: emptyList()
                val delayMs = intent.getLongExtra(EXTRA_DELAY_MS, 500L)
                startTestLoop(routerId, cardList, delayMs)
            }
            ACTION_PAUSE -> {
                _serviceState.update { it.copy(isPaused = true, status = "PAUSED", sessionState = com.example.domain.model.TestSessionState.PAUSED) }
                notificationHelper.updateNotification(_serviceState.value)
            }
            ACTION_RESUME -> {
                _serviceState.update { it.copy(isPaused = false, status = "RUNNING", sessionState = com.example.domain.model.TestSessionState.RUNNING) }
                notificationHelper.updateNotification(_serviceState.value)
            }
            ACTION_CANCEL -> {
                if (retryDeferred != null) {
                    retryDeferred?.complete(false)
                } else {
                    cancelTest()
                }
            }
            ACTION_RETRY_LOAD -> {
                retryDeferred?.complete(true)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun loadUrlWithGeneration(
        webView: WebView,
        url: String,
        timeoutMs: Long = 15000L
    ): Boolean = withContext(Dispatchers.Main) {
        val gen = navigationGenerationSeq.incrementAndGet()
        val def = CompletableDeferred<Unit>()
        activeNavigations[webView]?.deferred?.cancel()
        activeNavigations[webView] = NavigationToken(gen, def, url)
        webView.stopLoading()
        webView.loadUrl(url)
        try {
            kotlinx.coroutines.withTimeout(timeoutMs) { def.await() }
            true
        } catch (e: Exception) {
            Timber.w("Navigation timeout or error for $url: ${e.message}")
            false
        }
    }

    private suspend fun ensureLoggedOut(router: RouterProfileEntity) {
        withContext(Dispatchers.Main) {
            try {
                val url = router.getFullLoginUrl()

                // Check and logout for each webview in the pool
                webViewPool.forEach { wv ->
                    wv.stopLoading()
                    delay(50L)

                    var isLoaded = false
                    while (!isLoaded && testJob?.isActive == true) {
                        isLoaded = loadUrlWithGeneration(wv, url, timeoutMs = 20000L)
                        if (!isLoaded) {
                            Timber.e("Error loading page to check logout state in webview. Retrying...")
                            delay(3000L)
                        }
                    }

                    // Settle check
                    delay(200L)
                    
                    val checkJs = InjectionManager.buildCheckResultJs(router.successIndicator, router.failureIndicator, router.submitSelector, router.logoutSelector)
                    val state = evaluateJsSafely(wv, checkJs)
                    
                    if (state == "success") {
                        Timber.d("User is already logged in prior to test! Logging out...")
                        val lJs = InjectionManager.buildLogoutJs(router.logoutSelector)
                        evaluateJsSafely(wv, lJs)
                        delay(2500L) // wait for logout to process
                        
                        // Load again just to be safe it's on the login page now
                        var isReloaded = false
                        while (!isReloaded && testJob?.isActive == true) {
                            isReloaded = loadUrlWithGeneration(wv, url, timeoutMs = 20000L)
                            if (!isReloaded) {
                                Timber.e("Error loading login page after forced logout. Retrying...")
                                delay(3000L)
                            }
                        }
                        delay(200L)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during ensureLoggedOut")
            }
        }
    }

    private suspend fun ensureFreshLoginPage(
        webView: WebView,
        router: RouterProfileEntity,
        strategy: RouterTestStrategy,
        maxWaitMs: Long = 4000L
    ): Boolean {
        // Navigate with a fresh generation token to ensure clean DOM
        loadUrlWithGeneration(webView, router.getFullLoginUrl(), timeoutMs = maxWaitMs)

        // Purge stale inputs and DOM alerts from previous card
        val cleanJs = """
        (function() {
            try {
                var inputs = document.querySelectorAll('input[type="text"], input[type="password"], #username, #uname, #password');
                for (var i = 0; i < inputs.length; i++) { inputs[i].value = ''; }
                var oldAlerts = document.querySelectorAll('.alert, .error, .alert-back, #danger');
                for (var j = 0; j < oldAlerts.length; j++) { oldAlerts[j].style.display = 'none'; }
            } catch(e) {}
        })();
        """.trimIndent()
        evaluateJsSafely(webView, cleanJs)

        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < maxWaitMs) {
            if (strategy.verifyFreshLoginPage(router, webView) { js -> evaluateJsSafely(webView, js) }) {
                return true
            }
            delay(150L)
        }
        return strategy.verifyFreshLoginPage(router, webView) { js -> evaluateJsSafely(webView, js) }
    }

    suspend fun ensureFreshLoginPageWithRecovery(
        webView: WebView,
        router: RouterProfileEntity,
        strategy: RouterTestStrategy,
        maxRetries: Int = 3,
        perAttemptTimeoutMs: Long = 4000L
    ): Boolean {
        for (attempt in 1..maxRetries) {
            val isReady = ensureFreshLoginPage(webView, router, strategy, maxWaitMs = perAttemptTimeoutMs)
            if (isReady) return true
            Timber.w("Fresh login page check failed on attempt $attempt/$maxRetries. Attempting recovery...")
            delay(500L)
        }
        return false
    }

    private fun triggerVibrationOnSuccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(500L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(500L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(500L)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to vibrate on success")
        }
    }

    private fun playSuccessSound() {
        try {
            val notificationUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            ringtone?.play()
        } catch (e: Exception) {
            Timber.e(e, "Failed to play success sound")
        }
    }

    private suspend fun evaluateJsSafely(webViewToUse: WebView?, js: String): String {
        if (webViewToUse == null) return "unknown"
        return kotlinx.coroutines.withTimeoutOrNull(5000L) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                webViewToUse.evaluateJavascript(js) { res ->
                    if (continuation.isActive) {
                        val cleanRes = res?.trim('"')?.trim() ?: "unknown"
                        continuation.resume(cleanRes)
                    }
                }
            }
        } ?: "unknown"
    }

    private suspend fun finalizeCardAttempt(
        attempt: CardAttempt,
        outcome: CardTestOutcome,
        router: RouterProfileEntity,
        strategy: RouterTestStrategy,
        webView: WebView,
        progressCounter: AtomicInteger,
        successCounter: AtomicInteger,
        failureCounter: AtomicInteger,
        isBlockedBySuccess: AtomicBoolean,
        stateMutex: Mutex,
        cardListSize: Int,
        delayMs: Long,
        lifecycleStateConsumer: (CardLifecycleState) -> Unit
    ): CardLifecycleState {
        val attemptKey = "${attempt.sessionId}_${attempt.cardIndex}_${attempt.workerId}_${attempt.attemptSequence}"
        if (!finalizedAttempts.add(attemptKey)) {
            Timber.w("Duplicate finalization detected for $attemptKey. Ignoring duplicate callback.")
            return CardLifecycleState.READY_FOR_NEXT
        }

        val terminalState = when (outcome.state) {
            "Success" -> CardLifecycleState.SUCCESS
            "Timeout" -> CardLifecycleState.TIMEOUT
            "Network_Error" -> CardLifecycleState.NETWORK_ERROR
            "Engine_Error" -> CardLifecycleState.ENGINE_ERROR
            else -> CardLifecycleState.FAILURE
        }
        lifecycleStateConsumer(terminalState)

        var isAuthoritativeSuccess = false
        if (outcome.isSuccess) {
            isAuthoritativeSuccess = isBlockedBySuccess.compareAndSet(false, true)
            if (!isAuthoritativeSuccess) {
                Timber.w("Card ${maskCard(attempt.cardCode)} reported success, but discarded as false positive (another worker claimed success first)")
            }
        }

        // 1. Authoritative persistence in Room SQLite FIRST (Barrier Step 1 & 2)
        val structuredCategory = if (isAuthoritativeSuccess) ResultCategory.SUCCESS.name else outcome.category.name
        val structuredSubReason = if (isAuthoritativeSuccess) {
            if (outcome.isTwoDevicesSuccess) ResultSubReason.TWO_DEVICES_SUCCESS.name else ResultSubReason.NORMAL_LOGIN_SUCCESS.name
        } else outcome.subReason.name
        val structuredSuccessReason = if (isAuthoritativeSuccess) outcome.successReason else null

        withContext(Dispatchers.IO) {
            testResultRepository.insertResult(
                TestResultEntity(
                    sessionId = attempt.sessionId,
                    cardCode = attempt.cardCode,
                    routerId = router.id,
                    routerName = router.name,
                    state = if (isAuthoritativeSuccess) "Success" else outcome.state,
                    message = outcome.message,
                    durationMs = outcome.durationMs,
                    testedAt = System.currentTimeMillis(),
                    category = structuredCategory,
                    subReason = structuredSubReason,
                    successReason = structuredSuccessReason
                )
            )
        }

        // 2. Increment progress and update counters AFTER authoritative persistence (Barrier Step 3)
        val currentProgress = progressCounter.incrementAndGet()
        if (isAuthoritativeSuccess) {
            val currentSuccess = successCounter.incrementAndGet()
            stateMutex.withLock {
                _serviceState.update {
                    it.copy(
                        progress = currentProgress,
                        successCount = currentSuccess
                    )
                }
                notificationHelper.showResultNotification(attempt.cardCode, true)
            }
            // Real vibration & audio feedback on confirmed success, respecting settings & not repeating
            try {
                if (appPreferences.vibrateOnSuccess.first()) {
                    triggerVibrationOnSuccess()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error triggering vibrateOnSuccess")
            }
            try {
                if (appPreferences.soundOnSuccess.first()) {
                    playSuccessSound()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error playing soundOnSuccess")
            }
        } else {
            // Technical errors (TIMEOUT, NETWORK_ERROR, ENGINE_ERROR) are NOT card failures
            val isTechnicalError = outcome.category == ResultCategory.TIMEOUT ||
                    outcome.category == ResultCategory.NETWORK_ERROR ||
                    outcome.category == ResultCategory.ENGINE_ERROR
            val currentFailure = if (!isTechnicalError) failureCounter.incrementAndGet() else failureCounter.get()
            stateMutex.withLock {
                _serviceState.update {
                    it.copy(
                        progress = currentProgress,
                        failureCount = currentFailure
                    )
                }
            }
        }

        val now = SystemClock.elapsedRealtime()
        val lastPost = lastNotificationUpdateTime.get()
        if (now - lastPost >= 800L || currentProgress >= cardListSize || isAuthoritativeSuccess) {
            lastNotificationUpdateTime.set(now)
            notificationHelper.updateNotification(_serviceState.value)
        }

        val shouldSyncSession = isAuthoritativeSuccess || (currentProgress % 10 == 0) || (currentProgress >= cardListSize)
        if (shouldSyncSession) {
            withContext(Dispatchers.IO) {
                sessionRepository.updateCounts(
                    sessionId = attempt.sessionId,
                    successCount = successCounter.get(),
                    failureCount = failureCounter.get()
                )
            }
        }

        // 3. Clean session transition & logout (Barrier Step 5 & 6)
        if (isAuthoritativeSuccess) {
            if (!outcome.isTwoDevicesSuccess) {
                lifecycleStateConsumer(CardLifecycleState.LOGOUT_REQUESTED)
                Timber.d("Normal login success. Logging out before next card...")
                delay(500L)
                withContext(Dispatchers.Main) {
                    val lJs = InjectionManager.buildLogoutJs(router.logoutSelector)
                    evaluateJsSafely(webView, lJs)
                    delay(2500L) // Settle router lease
                }
                lifecycleStateConsumer(CardLifecycleState.LOGOUT_CONFIRMED)
            } else {
                Timber.d("Card is twoDevicesSuccess. No router logout needed (session was not established on router).")
            }
            isBlockedBySuccess.set(false)
        }

        // 4. Resetting WebView & Confirming Fresh Login Page (Barrier Step 7)
        lifecycleStateConsumer(CardLifecycleState.RESETTING)
        val resetSuccess = ensureFreshLoginPageWithRecovery(webView, router, strategy)
        delay(delayMs)

        // 5. Readiness confirmed (Barrier Step 8)
        if (!resetSuccess) {
            Timber.e("Failed to restore fresh login page during card finalize. Suspending transition to READY_FOR_NEXT.")
            lifecycleStateConsumer(CardLifecycleState.FAILED_BARRIER)
            return CardLifecycleState.FAILED_BARRIER
        }

        lifecycleStateConsumer(CardLifecycleState.READY_FOR_NEXT)
        return CardLifecycleState.READY_FOR_NEXT
    }

    private fun startTestLoop(routerId: Long, cardList: List<String>, delayMs: Long) {
        testJob?.cancel()
        testJob = serviceScope.launch {
            try {
                val enablePreload = appPreferences.enablePreload.first()
                val requestedPoolSize = if (enablePreload) appPreferences.threadCount.first() else 1
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val isLowRam = activityManager?.isLowRamDevice == true
                val maxSafePool = if (isLowRam) 1 else 3
                val poolSize = requestedPoolSize.coerceIn(1, maxSafePool)
                
                val router = withContext(Dispatchers.IO) {
                    routerRepository.getById(routerId)
                } ?: run {
                    _serviceState.update { it.copy(status = "LOAD_ERROR", sessionState = com.example.domain.model.TestSessionState.FAILED, error = "Router profile not found") }
                    return@launch
                }
                currentTargetRouterIp = router.ip

                val sessionId = withContext(Dispatchers.IO) {
                    sessionRepository.createSession(routerId, router.name)
                }
                currentSessionId = sessionId

                _serviceState.update {
                    ServiceState(
                        total = cardList.size,
                        status = "RUNNING",
                        sessionState = com.example.domain.model.TestSessionState.RUNNING,
                        progress = 0,
                    )
                }

                startScreenshotLoop()
                
                val url = router.getFullLoginUrl()

                // Initialize WebView Pool
                withContext(Dispatchers.Main) {
                    webViewPool.forEach { 
                        try {
                            it.stopLoading()
                            it.destroy()
                        } catch (_: Throwable) {}
                    }
                    webViewPool.clear()
                    activeNavigations.clear()
                    
                    for (i in 0 until poolSize) {
                        val wv = createWebViewInstance()
                        webViewPool.add(wv)
                    }
                }

                // Preload and ensure logged out for all WebViews
                withContext(Dispatchers.Main) {
                    webViewPool.forEach { 
                        loadUrlWithGeneration(it, url, timeoutMs = 15000L)
                    }
                }
                
                // Perform initial logout check after pool is initialized and let them load
                ensureLoggedOut(router)
                val strategy = RouterStrategyFactory.getStrategy(router)
                webViewPool.forEach { wv ->
                    ensureFreshLoginPageWithRecovery(wv, router, strategy)
                }

                val cardQueue = Channel<String>(capacity = 64)
                val feederJob = launch {
                    try {
                        for (card in cardList) {
                            cardQueue.send(card)
                        }
                    } finally {
                        cardQueue.close()
                    }
                }

                val progressCounter = AtomicInteger(0)
                val successCounter = AtomicInteger(0)
                val failureCounter = AtomicInteger(0)
                val isBlockedBySuccess = AtomicBoolean(false)
                val stateMutex = Mutex()
                val isRelogging = AtomicBoolean(false)

                val workers = webViewPool.mapIndexed { wvIndex, webView ->
                    launch {
                        var cardToRetry: String? = null
                        val attemptSeq = AtomicInteger(0)
                        var cardIndex = 0

                        for (rawCard in cardQueue) {
                            var currentCard = cardToRetry ?: rawCard
                            cardToRetry = null
                            cardIndex++

                            var lifecycleState = CardLifecycleState.QUEUED

                            // Check pause states
                            while (_serviceState.value.isPaused || isBlockedBySuccess.get() || isRelogging.get()) {
                                delay(500)
                            }

                            // Deterministic internal identity & generation token
                            val currentGen = navigationGenerationSeq.incrementAndGet()
                            val attempt = CardAttempt(
                                sessionId = sessionId,
                                cardIndex = cardIndex,
                                cardCode = currentCard,
                                workerId = wvIndex,
                                attemptSequence = attemptSeq.incrementAndGet(),
                                generationToken = currentGen
                            )

                            // Update active card display ONLY (do NOT increment progress yet!)
                            stateMutex.withLock {
                                _serviceState.update {
                                    it.copy(currentCard = currentCard)
                                }
                            }

                            currentWebViewIndex = wvIndex // For screenshots
                            capturedAlerts[webView] = Pair(currentGen, "")

                            lifecycleState = CardLifecycleState.NAVIGATING
                            val startTime = SystemClock.elapsedRealtime()
                            var wasInterrupted = false

                            // Verify actual login page readiness with limited recovery attempts and timeout
                            val isPageReady = ensureFreshLoginPageWithRecovery(webView, router, strategy)
                            if (!isPageReady) {
                                Timber.e("Login page not ready for card ${maskCard(currentCard)}. Aborting worker with technical error.")
                                val duration = SystemClock.elapsedRealtime() - startTime
                                val errorOutcome = CardTestOutcome.networkError(
                                    message = "صفحة الدخول غير جاهزة بعد محاولات الاستعادة",
                                    durationMs = duration
                                )
                                finalizeCardAttempt(
                                    attempt = attempt,
                                    outcome = errorOutcome,
                                    router = router,
                                    strategy = strategy,
                                    webView = webView,
                                    progressCounter = progressCounter,
                                    successCounter = successCounter,
                                    failureCounter = failureCounter,
                                    isBlockedBySuccess = isBlockedBySuccess,
                                    stateMutex = stateMutex,
                                    cardListSize = cardList.size,
                                    delayMs = delayMs,
                                    lifecycleStateConsumer = { st -> lifecycleState = st }
                                )
                                // Safely suspend/stop worker, do NOT classify as card failure and do NOT test next card
                                break
                            }

                            lifecycleState = CardLifecycleState.LOGIN_PAGE_READY

                            val outcome = try {
                                if (isBlockedBySuccess.get()) {
                                    wasInterrupted = true
                                    throw IllegalStateException("Interrupted by success")
                                }
                                
                                // Check for reload buttons immediately on loaded DOM
                                val checkReloadJs = """
                                    (function() {
                                        if (document.readyState !== 'complete' && document.readyState !== 'interactive') return 'not_ready';
                                        var buttons = document.querySelectorAll('button, a, input[type="button"]');
                                        for (var i = 0; i < buttons.length; i++) {
                                            var text = (buttons[i].innerText || buttons[i].value || '').toLowerCase();
                                            if (text.indexOf('إعادة تحميل') !== -1 || text.indexOf('retry') !== -1 || text.indexOf('reload') !== -1) {
                                                buttons[i].click();
                                                return 'clicked_reload';
                                            }
                                        }
                                        return 'ok';
                                    })();
                                """.trimIndent()
                                val reloadStatus = evaluateJsSafely(webView, checkReloadJs)
                                if (reloadStatus == "clicked_reload") {
                                    Timber.d("Clicked reload button during test loop, waiting for reload...")
                                    delay(300L)
                                    ensureFreshLoginPageWithRecovery(webView, router, strategy)
                                }
                                
                                val onRequiresGlobalRelogin: suspend () -> Unit = {
                                    if (isRelogging.compareAndSet(false, true)) {
                                        Timber.d("Global relogin triggered by a webview. Pausing all others...")
                                        delay(500)
                                        val lJs = InjectionManager.buildLogoutJs(router.logoutSelector)
                                        evaluateJsSafely(webView, lJs)
                                        delay(2000)
                                        
                                        webViewPool.forEach { wv ->
                                            loadUrlWithGeneration(wv, router.getFullLoginUrl(), timeoutMs = 15000L)
                                        }
                                        webViewPool.forEach { wv ->
                                            ensureFreshLoginPageWithRecovery(wv, router, strategy)
                                        }
                                        delay(1000)
                                        isRelogging.set(false)
                                        Timber.d("Global relogin complete. Resuming all...")
                                    } else {
                                        while (isRelogging.get()) { delay(500) }
                                    }
                                }
                                
                                lifecycleState = CardLifecycleState.SUBMITTING
                                lifecycleState = CardLifecycleState.WAITING_RESULT

                                strategy.testCardWithDetails(
                                    card = currentCard,
                                    router = router,
                                    webView = webView,
                                    evaluateJsSafely = { js -> evaluateJsSafely(webView, js) },
                                    pauseCondition = { while (_serviceState.value.isPaused) { delay(500) } },
                                    isPreloaded = true,
                                    onRequiresGlobalRelogin = onRequiresGlobalRelogin,
                                    isBlockedBySuccess = { isBlockedBySuccess.get() },
                                    capturedAlert = {
                                        val alertPair = capturedAlerts[webView]
                                        if (alertPair != null && alertPair.first >= attempt.generationToken) alertPair.second else ""
                                    }
                                )
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.e(e, "Worker $wvIndex failed for card: ${maskCard(currentCard)}")
                                CardTestOutcome.engineError(e.localizedMessage ?: "فشل في محرك الاختبار", 0L)
                            }
                            val duration = SystemClock.elapsedRealtime() - startTime
                            val finalOutcome = outcome.copy(durationMs = duration)

                            // Check if interrupted by another worker claiming success
                            val attemptKey = "${attempt.sessionId}_${attempt.cardIndex}_${attempt.workerId}_${attempt.attemptSequence}"
                            if (finalOutcome.isSuccess && isBlockedBySuccess.get() && !finalizedAttempts.contains(attemptKey)) {
                                wasInterrupted = true
                            }

                            if (wasInterrupted) {
                                Timber.d("Card ${maskCard(currentCard)} was interrupted by another thread's success. Will retry later.")
                                cardToRetry = currentCard
                                while (isBlockedBySuccess.get()) { delay(500) }
                                ensureFreshLoginPageWithRecovery(webView, router, strategy)
                                delay(delayMs)
                                continue
                            }

                            // Strict terminal finalization barrier (authoritative result -> DB -> progress -> logout -> reset -> ready)
                            lifecycleState = finalizeCardAttempt(
                                attempt = attempt,
                                outcome = finalOutcome,
                                router = router,
                                strategy = strategy,
                                webView = webView,
                                progressCounter = progressCounter,
                                successCounter = successCounter,
                                failureCounter = failureCounter,
                                isBlockedBySuccess = isBlockedBySuccess,
                                stateMutex = stateMutex,
                                cardListSize = cardList.size,
                                delayMs = delayMs,
                                lifecycleStateConsumer = { st -> lifecycleState = st }
                            )

                            // Terminal barrier rule: READY_FOR_NEXT is the ONLY state permitting consumption of the next card
                            if (lifecycleState != CardLifecycleState.READY_FOR_NEXT) {
                                Timber.e("Card ${maskCard(currentCard)} attempt $attempt suspended/failed lifecycle barrier with state $lifecycleState. Stopping worker safely.")
                                break
                            }
                        }
                    }
                }

                workers.forEach { it.join() }
                feederJob.cancel()

                _serviceState.update { it.copy(status = "COMPLETING", sessionState = com.example.domain.model.TestSessionState.COMPLETING) }
                withContext(Dispatchers.IO) {
                    sessionRepository.markFinished(
                        sessionId = sessionId,
                        successCount = successCounter.get(),
                        failureCount = failureCounter.get()
                    )
                }

                _serviceState.update { it.copy(status = "DONE", sessionState = com.example.domain.model.TestSessionState.COMPLETED) }
                notificationHelper.updateNotification(_serviceState.value)
                stopSelf()

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Error during test loop")
                _serviceState.update { it.copy(status = "LOAD_ERROR", sessionState = com.example.domain.model.TestSessionState.FAILED, error = e.localizedMessage) }
            }
        }
    }

    private suspend fun testCard(
        card: String,
        router: RouterProfileEntity,
        isPreloaded: Boolean,
        webViewToUse: WebView? = activeWebView,
        onRequiresGlobalRelogin: (suspend () -> Unit)? = null,
        isBlockedBySuccess: () -> Boolean = { false }
    ): Boolean {
        val wv = webViewToUse ?: activeWebView
        val strategy = RouterStrategyFactory.getStrategy(router)
        Timber.d("Testing card on router ${router.name} using strategy: ${strategy.strategyName}")
        return strategy.testCard(
            card = card,
            router = router,
            webView = wv,
            evaluateJsSafely = { js -> evaluateJsSafely(wv, js) },
            pauseCondition = { while (_serviceState.value.isPaused) { delay(500) } },
            isPreloaded = isPreloaded,
            onRequiresGlobalRelogin = onRequiresGlobalRelogin,
            isBlockedBySuccess = isBlockedBySuccess
        )
    }

    private fun startScreenshotLoop() {
        screenshotJob?.cancel()
        screenshotJob = serviceScope.launch {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@TestService)
            while (true) {
                val delayMs = prefs.getString("screenshot_delay", "2000")?.toLongOrNull() ?: 2000L
                delay(delayMs)
                val bitmap = captureScreenshot()
                if (bitmap != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
                            val bytes = stream.toByteArray()
                            _serviceState.update { it.copy(screenshotBytes = bytes) }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to compress screenshot")
                        } finally {
                            try { bitmap.recycle() } catch (_: Throwable) {}
                        }
                    }
                }
            }
        }
    }

    private fun captureScreenshot(): Bitmap? {
        val view = activeWebView ?: return null
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return null
        return try {
            val scale = 360f / w.coerceAtLeast(1)
            val targetWidth = 360
            val targetHeight = (h * scale).toInt().coerceIn(1, 800)
            
            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
            val canvas = Canvas(bmp)
            canvas.scale(scale, scale)
            view.draw(canvas)
            bmp
        } catch (e: Throwable) {
            null
        }
    }

    private fun cancelTest() {
        currentTargetRouterIp = null
        testJob?.cancel()
        screenshotJob?.cancel()
        _serviceState.update { it.copy(status = "CANCELLED", sessionState = com.example.domain.model.TestSessionState.STOPPED) }
        notificationHelper.updateNotification(_serviceState.value)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        currentTargetRouterIp = null
        testJob?.cancel()
        screenshotJob?.cancel()
        serviceScope.cancel()
        try {
            capturedAlerts.clear()
            activeNavigations.clear()
            finalizedAttempts.clear()
            webViewPool.forEach { 
                try {
                    it.stopLoading()
                    it.destroy()
                } catch (_: Throwable) {}
            }
            webViewPool.clear()
        } catch (e: Throwable) {
            Timber.e(e, "Error destroying webview pool in onDestroy")
        }
        _serviceState.value = ServiceState()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}

