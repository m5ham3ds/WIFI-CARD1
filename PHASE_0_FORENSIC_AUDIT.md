# PHASE 0 — FORENSIC BASELINE AUDIT REPORT
**Target Application:** WiFi Card Master Pro / WD Master  
**Package / Application ID:** `com.aistudio.wifimasterpro.axfkyv` (Namespace: `com.example`)  
**Audit Date:** 2026-10-06  
**Auditor Role:** Senior Android Architect + Code Auditor + Performance Engineer + Security Reviewer  
**Audit Objective:** Complete, non-destructive, forensic baseline inspection of the entire codebase, architecture, build system, dependencies, security posture, performance bottlenecks, and test engine.

---

## 1. Executive Summary

A comprehensive, forensic code and architecture audit was conducted across the entire **WiFi Card Master Pro / WD Master** repository. In accordance with Phase 0 instructions, **zero modifications, refactoring, renames, or deletions** were performed on the application codebase.

The audit revealed an application that successfully compiles with system Gradle 9.3.1, but suffers from severe structural, performance, security, and architectural deficiencies that impede scalability, test execution speed, and security integrity:

1. **Build Blocker (P0):** The repository's `gradle/wrapper/gradle-wrapper.jar` is physically corrupted (`ZipException: zip END header not found`), preventing any build via standard `./gradlew`. CI and developers previously had to bypass wrapper verification (`validate-wrappers: false`) or rely on external Gradle installations.
2. **Security Vulnerabilities (P1):**
   - The master security unlock password (`"MOHAMED564"`) is hard-coded in plaintext in `SecurityViewModel.kt` (line 54).
   - WebView SSL certificate verification is unconditionally bypassed (`handler?.proceed()` in `TestService.kt` line 145), exposing HTTP/HTTPS traffic to Man-in-the-Middle (MitM) attacks.
   - `network_security_config.xml` enables cleartext traffic globally across the entire application.
   - `file_provider_paths.xml` exposes the application's root internal files directory (`path="."`) to external apps.
   - Router passwords and credentials are saved in plaintext in the Room database without encryption.
   - Android Backup Rules (`backup_rules.xml` & `data_extraction_rules.xml`) export all SharedPreferences, allowing full security lockout bypass and credential extraction via ADB backup.
3. **Severe Performance Bottlenecks (P1):**
   - **66 hard-coded `delay(...)` calls** populate the codebase, forcing tests to waste between 6.5 and 12 seconds per card. Testing 1,000 cards takes ~2.2 hours of pure idle sleep time.
   - `TestService` runs a continuous background screenshot loop that redraws the active WebView onto a Bitmap on the Main Thread every 2,000 ms, compresses it to JPEG, and publishes raw byte arrays into a `StateFlow` inside `ServiceState`. This triggers massive garbage collection (GC) churn and memory overhead.
   - Every individual card test triggers 2 distinct, synchronous SQLite database writes (`insertResult` + `updateCounts`), followed by a full custom `RemoteViews` notification recreation and a full WebView page reload.
   - `HomeViewModel` continuously re-queries and re-maps the entire session result list in memory on every card insertion.
4. **Architecture Degradation & Layer Violations (P2):**
   - Clean Architecture is broken: all 5 Domain Repository interfaces (`ICardRepository`, `IRouterRepository`, `ISessionRepository`, `ITestResultRepository`, `IPatternRepository`) directly expose Room Entity models (`*Entity.kt`) from the Data layer, bypassing Domain models (`Card`, `RouterProfile`, `TestResult`, `TestSession`).
   - Mappers (`CardMapper`, `RouterMapper`, `SessionMapper`) and Domain UseCases (`ImportResultsUseCase`) are completely dead, unused code.
   - `ResultChecker` is an empty, hollow object with all logic stripped.
   - The three router strategies (`AbashaTestStrategy`, `BelloTestStrategy`, `MotasemTestStrategy`) share ~90% duplicate code, are implemented as static singleton objects rather than polymorphic strategies, and are chosen via naive string-matching on the router name inside `TestService`.
5. **Database Integrity Risks (P1):**
   - Room Database is at `version = 4`, but only `MIGRATION_1_2` exists. Migrations from 2→3 and 3→4 are absent. The database builder specifies `.fallbackToDestructiveMigration()`, which will silently wipe all user data upon upgrading from older database versions.
   - Tables lack indexes on queried foreign keys (`sessionId`, `routerId`, `testedAt`).
6. **Localization Deficits (P3):**
   - `values-en/strings.xml` contains only 57 lines, while `values/strings.xml` and `values-ar/strings.xml` contain 169 lines. Over 110 English strings are missing.
   - Significant amounts of user-facing Arabic and English copy are hard-coded in Kotlin files and `nav_graph.xml`.

---

## 2. Project Statistics

| Metric | Count | Details |
|---|---|---|
| **Total Kotlin Files** | 80 | Located in `app/src/main/java` (79) and `app/src/test/java` (1) |
| **Total Kotlin Lines of Code** | 6,465 | 6,338 lines in `src/main/java` + 127 lines in `src/test/java` |
| **Total XML Resource Files** | 61 | Layouts (20), Values (8), Drawables (24), Menus (2), Nav (1), XML configs (6) |
| **Total Layout XML Files** | 20 | Including 2 dead layouts (`dialog_router_form.xml`, `view_log_terminal.xml`) |
| **Activities** | 1 | `MainActivity.kt` (Single Activity Architecture) |
| **Fragments** | 7 | `SecurityFragment`, `LockedFragment`, `HomeFragment`, `TestFragment`, `HistoryFragment`, `SettingsFragment`, `RouterManagerFragment`, `RouterFormFragment` |
| **ViewModels** | 6 | `HomeViewModel`, `HistoryViewModel`, `SettingsViewModel`, `RouterManagerViewModel`, `TestViewModel`, `SecurityViewModel` |
| **Services** | 1 | `TestService.kt` (Foreground Service, 893 LOC God Class) |
| **Broadcast Receivers** | 1 | `NotificationActionReceiver.kt` (Unused / dead receiver) |
| **Room Entities** | 5 | `CardEntity`, `RouterProfileEntity`, `TestResultEntity`, `TestSessionEntity`, `SuccessfulPatternEntity` |
| **Room DAOs** | 5 | `CardDao`, `RouterProfileDao`, `TestResultDao`, `SessionDao`, `PatternDao` |
| **Domain UseCases** | 4 | `GenerateCardsUseCase`, `ManageRoutersUseCase`, `ExportResultsUseCase`, `ImportResultsUseCase` |
| **Strategies** | 3 | `AbashaTestStrategy`, `BelloTestStrategy`, `MotasemTestStrategy` (Static Objects) |
| **Custom Views / Widgets** | 5 | `ConnectionStatusView`, `CustomProgressBar`, `CustomToolbar`, `LogTerminalView`, `StatisticCard` |
| **Unit Tests** | 0 Actual | `FileProcessorTest.kt` is a resource mutation script, not a test suite |

---

## 3. Architecture Map

### 3.1 Actual Architecture Implemented

The project attempts to follow an MVVM + Clean Architecture layout with Koin Dependency Injection, but suffers from layer leakage where Data layer entities bypass Domain models and are consumed directly by Presentation ViewModels and Domain Repositories.

```
┌────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                            │
│  MainActivity  ──►  NavHost (nav_graph.xml)                            │
│     ├── SecurityFragment  ◄──►  SecurityViewModel                      │
│     ├── LockedFragment                                                 │
│     ├── HomeFragment      ◄──►  HomeViewModel                          │
│     ├── TestFragment      ◄──►  TestViewModel                          │
│     ├── HistoryFragment   ◄──►  HistoryViewModel                       │
│     ├── SettingsFragment  ◄──►  SettingsViewModel                      │
│     ├── RouterManagerFrag ◄──►  RouterManagerViewModel                 │
│     └── RouterFormFrag    ◄──►  RouterManagerViewModel                 │
│                                                                        │
│  UI Bindings: Manual findViewById (ViewBinding configured but unused)  │
│  State: StateFlow, SharedFlow, BaseViewModel (Coroutines)              │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Direct Entity Leaks
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                            DOMAIN LAYER                                │
│  UseCases:                                                             │
│    - GenerateCardsUseCase (Imports CardEntity directly from Data layer)│
│    - ManageRoutersUseCase (Exposes RouterProfileEntity to ViewModels)  │
│    - ExportResultsUseCase (Reads TestResultEntity directly)            │
│    - ImportResultsUseCase (Unused dead code)                           │
│                                                                        │
│  Repository Interfaces (LAYER VIOLATION: all return Room Entities):   │
│    - ICardRepository        ──► returns Flow<List<CardEntity>>         │
│    - IRouterRepository      ──► returns Flow<List<RouterProfileEntity>>│
│    - ISessionRepository     ──► returns Flow<List<TestSessionEntity>>  │
│    - ITestResultRepository  ──► returns Flow<List<TestResultEntity>>   │
│    - IPatternRepository     ──► Flow<List<SuccessfulPatternEntity>>    │
│                                                                        │
│  Domain Models (MOSTLY UNUSED / BYPASSED):                             │
│    Card, RouterProfile, TestResult, TestSession                        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                             DATA LAYER                                 │
│  Repositories: CardRepository, RouterRepository, SessionRepository,    │
│                TestResultRepository, PatternRepository                 │
│  Room DB (version 4): AppDatabase (wdmaster_db)                        │
│    Entities: CardEntity, RouterProfileEntity, TestResultEntity,        │
│              TestSessionEntity, SuccessfulPatternEntity                │
│    DAOs: CardDao, RouterProfileDao, TestResultDao, SessionDao,         │
│          PatternDao                                                    │
│  Preferences: AppPreferences (DataStore), ThemePreferences (DataStore),│
│               SharedPreferences (Default, "locale_prefs", "theme_prefs")│
└────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Real Test Execution Data Flow

The actual test execution flow does **not** route from `UI → ViewModel → UseCase → Repository → Service`. Instead, it uses direct Android Intents and static Companion objects across components:

```
[HomeFragment] User presses "Start Test"
   │
   ├─► Validates UI inputs & checks Wi-Fi connection
   ├─► HomeViewModel.generateAndStart()
   │     └─► GenerateCardsUseCase(prefix, len, count, charset)
   │           ├─► Generates codes via SecureRandom
   │           ├─► cardRepository.deleteAll()
   │           └─► cardRepository.insertCards(CardEntity...) [Room: cards table]
   │
   ├─► HomeViewModel emits TestStartConfig via SharedFlow
   ├─► HomeFragment catches event and constructs Intent with EXTRA_CARD_LIST (ArrayList<String>)
   │     (Risk: TransactionTooLargeException if list > 500KB)
   │
   ├─► ContextCompat.startForegroundService(TestService)
   │
   ▼
[TestService] (Foreground Service with DATA_SYNC type)
   │
   ├─► onCreate(): Initializes NotificationHelper, sets static TestService.isRunning = true
   ├─► onStartCommand(): Shows initial notification, calls startTestLoop()
   │
   ├─► Creates Session in Room DB: sessionRepository.createSession()
   ├─► Spawns screenshotJob (Captures WebView canvas every 2000ms -> JPEG ByteArray in StateFlow)
   │
   ├─► Instantiates WebView Pool (Size from DataStore threadCount, up to 100 WebViews)
   ├─► Loads Router URL on all WebViews: webView.loadUrl(url)
   ├─► ensureLoggedOut(): Loops over WebViews, loads URL, waits 2000ms-3000ms, evaluates logout JS
   │
   ├─► Feeds cards into Coroutine Channel: cardQueue
   ├─► Spawns N worker coroutines (one per WebView):
   │     │
   │     For each card in cardQueue:
   │       ├─► Updates StateFlow: currentCard, progress
   │       ├─► notificationHelper.updateNotification() [Re-inflates RemoteViews on EVERY card]
   │       ├─► String-matches router name:
   │       │     - If contains "معتصم" -> MotasemTestStrategy.testMotasemCard()
   │       │     - If contains "بيلو" -> BelloTestStrategy.testBelloCard()
   │       │     - If contains "اباشا" -> AbashaTestStrategy.testAbashaCard()
   │       │     - Else -> Inline testCard()
   │       │
   │       ├─► Strategy evaluates JS (Injected via InjectionManager):
   │       │     - Pre-flight check (delay 2500ms)
   │       │     - Form ready check (loop up to 20s)
   │       │     - JS Injection (values + triggerEvents)
   │       │     - Delay cardTestDelay (3000ms - 3500ms)
   │       │     - Result check JS (Checks text, indicators, "authorizing")
   │       │     - If success: forces logout JS (delay 2500ms)
   │       │
   │       ├─► TestResultRepository.insertResult() [Room DB write per card!]
   │       ├─► SessionRepository.updateCounts() [Room DB write per card!]
   │       ├─► NotificationHelper.showResultNotification() if success
   │       ├─► Resets WebView: clears innerHTML, history, formData, reloads URL
   │       └─► Waits delay(delayMs) (2000ms)
   │
   ├─► SessionRepository.markFinished() [Room DB update]
   └─► stopSelf()
```

### 3.3 State Management & Static Coupling

The application relies heavily on **static global state** rather than bounded dependency-injected state flows:
- `TestService.isRunning` (`Companion object StateFlow<Boolean>`) is observed directly by `HomeViewModel`, `MainActivity`, and `TestFragment`.
- `TestService.serviceState` (`Companion object StateFlow<ServiceState>`) is observed directly by `TestViewModel` and `TestFragment`.
- `ServiceBinder` holds a direct strong reference to `TestService` via `fun getService(): TestService = service`.

---

## 4. Build System Audit

### 4.1 System & Toolchain Configuration

| Configuration Item | Configured Value | Status / Finding |
|---|---|---|
| **Gradle Wrapper Script** | `gradlew` (8,618 bytes) | Not executable by default (`chmod -x`). |
| **Gradle Wrapper JAR** | `gradle/wrapper/gradle-wrapper.jar` (78,783 bytes) | **CORRUPTED (P0 BLOCKER):** Invalid zip header (`ZipException: zip END header not found`). Fails build immediately. |
| **Gradle Distribution** | `gradle-9.3.1-bin.zip` in `gradle-wrapper.properties` | Points to Gradle 9.3.1. |
| **Installed System Gradle** | `/opt/gradle/gradle-9.3.1/bin/gradle` | Operational on Java 21.0.12.1. |
| **Android Gradle Plugin (AGP)** | `8.7.3` | Modern, compatible with Gradle 9.x. |
| **Kotlin Version** | `2.1.0` | Modern compiler. |
| **KSP Version** | `2.1.0-1.0.29` | Matches Kotlin 2.1.0. |
| **compileSdk / targetSdk** | `34` | Android 14. Target is current. |
| **minSdk** | `26` | Android 8.0 (Oreo). |
| **Java / JVM Target** | `17` (`JavaVersion.VERSION_17`) | Clean JVM target. Host runs Java 21. |
| **Build Tools** | `36.0.0` in `app/build.gradle.kts` | Sourced from `/opt/android/sdk/build-tools/36.0.0`. |
| **ViewBinding** | `buildFeatures { viewBinding = true }` | Enabled, classes generated, but ignored in code. |
| **BuildConfig** | `buildFeatures { buildConfig = true }` | Enabled. |

### 4.2 ProGuard / R8 Configuration

File: `app/proguard-rules.pro` (68 lines).
- Release build has `isMinifyEnabled = false` in `app/build.gradle.kts`. ProGuard rules are present but currently inactive during debug and standard release builds.
- Rules cover Kotlin metadata, Room, Koin, Coroutines, Serialization, Coil, Lottie, OkHttp, Timber, and WebView JavaScript Interface.
- **Flaw in WebView Interface Rule:**
  ```proguard
  -keepclassmembers class com.example.service.TestService$* {
      @android.webkit.JavascriptInterface public *;
  }
  ```
  `TestService` does not contain inner classes with `@JavascriptInterface`. Instead, it uses `evaluateJavascript()`. The rule targets non-existent classes.

### 4.3 Build Warnings & Deprecations Recorded During Compilation

1. **Manifest Namespace Deprecation:**
   ```
   package="com.example" found in source AndroidManifest.xml: /app/applet/app/src/main/AndroidManifest.xml.
   Setting the namespace via the package attribute in the source AndroidManifest.xml is no longer supported, and the value is ignored.
   Recommendation: remove package="com.example" from the source AndroidManifest.xml
   ```
2. **OnBackPressed Deprecation:**
   `MainActivity.kt:203:19`: `fun onBackPressed(): Unit` is deprecated in Android 13+ (API 33). Should migrate to `OnBackPressedDispatcher`.
3. **EncryptedSharedPreferences & MasterKeys Deprecation:**
   `SecurityUtils.kt:5:8, 24:34, 25:52, 44:34, 45:52`: `MasterKeys` and `EncryptedSharedPreferences.create()` are deprecated in modern AndroidX Security. Should use `MasterKey.Builder`.
4. **ViewCompat Deprecation:**
   `CustomProgressBar.kt:17:25`: `ViewCompat.isAttachedToWindow(View)` is deprecated. Should call `view.isAttachedToWindow`.
5. **Gradle 10 Incompatibilities:**
   Gradle 9.3.1 reports deprecated API usage incompatible with Gradle 10.

---

## 5. Dependency Audit

Audit of all dependencies in `gradle/libs.versions.toml` and `app/build.gradle.kts`:

| Dependency | Version | Used? | Where Used | Action | Reason / Recommendation |
|---|---|---|---|---|---|
| `androidx-core-ktx` | `1.13.1` | **YES** | Across all activities, fragments, services | **Keep** | Essential Android core Kotlin extensions. |
| `androidx-appcompat` | `1.7.0` | **YES** | `MainActivity`, UI themes, dialogs | **Keep** | Essential backward compatibility. |
| `material` | `1.12.0` | **YES** | All layouts, buttons, cards, dialogs | **Keep** | Material 3 components. |
| `constraintlayout` | `2.2.0` | **NO** | Zero layouts use ConstraintLayout | **Remove** | Bloat; layouts use LinearLayout/RelativeLayout. |
| `swiperefreshlayout` | `1.1.0` | **NO** | Never referenced in code or XML | **Remove** | Completely unused dependency. |
| `navigation-fragment-ktx` | `2.8.5` | **YES** | `nav_graph.xml`, all Fragments | **Keep** | Modern Jetpack Navigation component. |
| `navigation-ui-ktx` | `2.8.5` | **YES** | `MainActivity.kt` | **Keep** | Integrates NavController with Toolbar and Drawer. |
| `lifecycle-viewmodel-ktx` | `2.8.7` | **YES** | All ViewModels, `viewModelScope` | **Keep** | Essential MVVM lifecycle component. |
| `lifecycle-runtime-ktx` | `2.8.7` | **YES** | `repeatOnLifecycle`, `lifecycleScope` | **Keep** | Essential safe Flow collection in UI. |
| `lifecycle-livedata-ktx` | `2.8.7` | **NO** | Zero references; uses StateFlow | **Remove** | Unused; app standardized on Coroutines Flow. |
| `room-runtime` | `2.6.1` | **YES** | `AppDatabase.kt`, DAOs | **Keep** | Local SQLite persistence. |
| `room-ktx` | `2.6.1` | **YES** | DAOs Coroutines Flow support | **Keep** | Essential for reactive Room queries. |
| `room-compiler` (KSP) | `2.6.1` | **YES** | Code generation for DAOs & DB | **Keep** | Required for Room annotation processing. |
| `datastore-preferences` | `1.1.2` | **YES** | `AppPreferences`, `ThemePreferences` | **Keep** | Modern asynchronous key-value store. |
| `koin-android` | `4.0.1` | **YES** | `MyApplication`, all Fragments | **Keep** | Lightweight Dependency Injection. |
| `koin-core` | `4.0.1` | **YES** | `AppModule`, `ViewModelModule` | **Keep** | Core DI engine. |
| `coroutines-android` | `1.10.1`| **YES** | Services, ViewModels, Repositories | **Keep** | Core asynchronous threading engine. |
| `coroutines-test` | `1.10.1`| **NO** | No actual unit tests present | **Keep** | Retain for future unit tests. |
| `coil` | `2.7.0` | **NO** | Zero imports in entire codebase | **Remove** | Image loader never invoked; app uses built-in Bitmaps. |
| `lottie` | `6.6.0` | **NO** | Zero animations or layouts use Lottie | **Remove** | Large dependency (~300KB DEX) completely unused. |
| `timber` | `5.0.1` | **YES** | Across all classes for logging | **Keep** | Structured logging utility. |
| `security-crypto` | `1.1.0-alpha06`| **YES** | `SecurityUtils.kt` | **Replace** | Obsolete alpha version; uses deprecated APIs (`MasterKeys`). |
| `kotlinx-serialization-json` | `1.8.0` | **YES** | `ExportResultsUseCase`, Models | **Keep** | JSON serialization for result export. |
| `okhttp` | `4.12.0` | **NO** | Zero network calls via OkHttp | **Remove** | Heavy dependency unused; all traffic goes through WebView. |
| `preference-ktx` | `1.2.1` | **YES** | `SettingsFragment` | **Consolidate** | Conflicts with DataStore. Consolidate to DataStore. |
| `webkit` | `1.12.1` | **NO** | Code uses `android.webkit.*` directly | **Remove** | AndroidX WebKit wrapper is never imported. |
| `junit` | `4.13.2` | **YES** | `FileProcessorTest.kt` | **Keep** | Required for unit test harness. |
| `junit-ext` | `1.2.1` | **NO** | No Android instrumentation tests | **Keep** | Standard test runner dependency. |
| `espresso-core` | `3.6.1` | **NO** | Zero UI tests exist | **Remove** | Unused testing library. |
| `mockito-kotlin` | `5.4.0` | **NO** | Zero mock-based unit tests exist | **Keep** | Retain for future test creation. |
| `room-testing` | `2.6.1` | **NO** | Zero database test classes exist | **Keep** | Retain for future DB test creation. |

---

## 6. Full Code Quality Audit

### 6.1 God Classes & Monolithic Services

- **`TestService.kt` (893 LOC) — Severity: CRITICAL**
  - **Problem:** Monolithic God Service violating Single Responsibility Principle (SRP).
  - **Evidence:** Manages Android Foreground Service lifecycle, notification updates, multi-threaded worker pools, channel queueing, WebView pool instantiation, page loading timeouts, screenshot capturing loop, bitmap JPEG compression, database writes, strategy selection via string matching, and error recovery.
  - **Impact:** Fragile code, memory leaks, unmaintainable testing logic, high crash risk.

### 6.2 Dead Code, Unused Classes, and Unused Functions

| File | Line / Element | Problem | Severity |
|---|---|---|---|
| `service/ResultChecker.kt` | Lines 5-8 (`object ResultChecker`) | Empty object with zero methods; comment states logic was moved to JS. | HIGH |
| `data/mapper/RouterMapper.kt` | Lines 6-52 (`object RouterMapper`) | Never imported or used anywhere in `app/src/`. | MEDIUM |
| `data/mapper/CardMapper.kt` | Lines 6-25 (`object CardMapper`) | Never imported or used anywhere in `app/src/`. | MEDIUM |
| `data/mapper/SessionMapper.kt`| Lines 6-19 (`object SessionMapper`) | Never imported or used anywhere in `app/src/`. | MEDIUM |
| `domain/usecase/ImportResultsUseCase.kt` | Lines 8-25 (`class ImportResultsUseCase`) | Defined and registered in DI, but never invoked by any ViewModel or UI. | MEDIUM |
| `data/repository/PatternRepository.kt` | Lines 8-22 (`PatternRepository`) | Defined and bound in DI, but never called by any feature in the app. | LOW |
| `domain/repository/IPatternRepository.kt` | Lines 6-11 (`IPatternRepository`) | Interface never injected or invoked. | LOW |
| `data/local/database/PatternDao.kt` | Lines 8-20 (`PatternDao`) | DAO never invoked. | LOW |
| `data/local/entity/SuccessfulPatternEntity.kt` | Lines 19-25 (`SuccessfulPatternEntity`) | Room table never populated or read. | LOW |
| `util/FileUtils.kt` | Lines 7-36 (`object FileUtils`) | Never referenced or called in the application. | LOW |
| `util/ValidationUtils.kt` | Lines 3-20 (`object ValidationUtils`) | Never referenced or called in the application. | LOW |
| `util/PermissionHelper.kt` | Lines 9-20 (`object PermissionHelper`) | `MainActivity` checks permissions inline, ignoring this helper. | LOW |
| `widget/LogTerminalView.kt` | Lines 18-63 (`class LogTerminalView`) | Custom View never placed in any layout or Kotlin file. | LOW |
| `res/layout/view_log_terminal.xml` | Layout file | Never inflated or referenced. | LOW |
| `res/layout/dialog_router_form.xml` | Layout file | Never inflated; `fragment_router_form.xml` is used instead. | LOW |
| `service/NotificationActionReceiver.kt` | Lines 9-30 (`NotificationActionReceiver`) | Registered in Manifest, but notifications use `PendingIntent.getService` directly. | MEDIUM |
| `util/Constants.kt` | Lines 3-30 (`object Constants`) | Zero constants are used; values are duplicated as magic numbers across files. | MEDIUM |
| `util/AppLogger.kt` | Lines 23-46 (`AppLogger.log`) | `log()` method is never called in the entire app; only `init()` is called in Application. | LOW |
| `presentation/test/TestViewModel.kt` | Lines 46-49 (`pauseTest()`) | Empty function body (`// Handled by posting custom pauses...`). | MEDIUM |
| `presentation/test/TestViewModel.kt` | Lines 19-20 | Injects `sessionRepository` and `testResultRepository`, but never uses them. | LOW |

### 6.3 Code Quality Anti-Patterns & Safety Issues

| File | Line | Issue | Severity | Impact |
|---|---|---|---|---|
| `presentation/security/SecurityViewModel.kt` | Line 54 | Hardcoded Plaintext Password: `if (password == "MOHAMED564")` | **CRITICAL** | Zero security; password is plain text in bytecode. |
| `presentation/MainActivity.kt` | Lines 100-101 | `runBlocking` on Main Thread: `runBlocking { appPreferences.isUnlocked.first() }` | **CRITICAL** | Blocks UI thread during `onCreate()`, causing ANR risk on slow I/O. |
| `service/AbashaTestStrategy.kt` | Line 28 | Unsafe Null Assertion `!!`: `webView!!.context` | **HIGH** | Crash with NPE if `webView` is null despite nullable signature. |
| `service/BelloTestStrategy.kt` | Line 28 | Unsafe Null Assertion `!!`: `webView!!.context` | **HIGH** | Crash with NPE if `webView` is null despite nullable signature. |
| `service/MotasemTestStrategy.kt` | Line 28 | Unsafe Null Assertion `!!`: `webView!!.context` | **HIGH** | Crash with NPE if `webView` is null despite nullable signature. |
| `util/DateUtils.kt` | Lines 8-10 | Thread-Unsafe `SimpleDateFormat` instances stored in singleton `object`. | **HIGH** | Concurrent formatting across background threads causes crashes or corrupted strings. |
| `util/AppLogger.kt` | Lines 38-39 | In-memory log truncation: `file.readLines()` and `lines.joinToString()` on 5MB file. | **MEDIUM** | Allocates 10MB+ heap in memory at once during log rotation. |
| `util/SecurityUtils.kt` | Lines 25, 45 | Recreates `EncryptedSharedPreferences` on every call. | **MEDIUM** | Inefficient Keystore and disk initialization overhead. |
| `presentation/home/HomeViewModel.kt` | Line 156 | ViewModel method accepts Android `Context`: `startMonitoringConnection(context: Context)`. | **MEDIUM** | Leaks Activity context if passed from Fragment. |
| `presentation/history/HistoryViewModel.kt` | Line 68 | ViewModel method accepts Android `Context`: `exportToFile(context: Context, ...)`. | **MEDIUM** | Presentation layer violation and memory leak risk. |

---

## 7. Performance Audit

### 7.1 Critical Performance Bottlenecks (CRITICAL)

1. **Continuous Main Thread Canvas Screenshot Rendering & StateFlow Garbage (`TestService.kt` lines 783–826):**
   - **Mechanism:** `screenshotJob` wakes up every 2,000 ms, calls `captureScreenshot()` which runs `view.draw(canvas)` on the active WebView directly on the Main thread.
   - **Heap Churn:** It allocates a new `RGB_565` Bitmap, allocates a `ByteArrayOutputStream`, compresses it to JPEG at 50% quality, and copies it to a `ByteArray`.
   - **StateFlow Flooding:** It copies this byte array into `_serviceState.update { it.copy(screenshotBytes = bytes) }`. Because `ServiceState` is a data class containing `ByteArray`, every state emission computes byte-array hash codes and forces GC passes.
   - **UI Impact (`TestFragment.kt` lines 132–150):** On every single `ServiceState` emission, `TestFragment` spawns a coroutine to run `BitmapFactory.decodeByteArray()` and updates `ivLiveScreenshot.setImageBitmap()`. This causes continuous UI frame drops and garbage collection pauses.

2. **Excessive Idle `delay(...)` Sleeps:**
   - **Mechanism:** Normal test execution path executes between 6.5s and 12s of static `delay()` calls per card.
   - **Impact:** Testing 100 cards takes ~13 minutes; testing 1,000 cards takes over 2 hours.

3. **Synchronous SQLite Disk I/O per Card (`TestService.kt` lines 548–590):**
   - **Mechanism:** After testing every card, `TestService` executes two sequential Room operations on Dispatchers.IO:
     1. `testResultRepository.insertResult(TestResultEntity(...))`
     2. `sessionRepository.updateCounts(sessionId, successCount, failureCount)`
   - **Impact:** High SQLite journal lock contention; disk writes slow down worker coroutines.

### 7.2 High Performance Issues (HIGH)

1. **Unthrottled RemoteViews Notification Updates (`TestService.kt` line 406 & `NotificationHelper.kt` lines 167–178):**
   - **Mechanism:** `notificationHelper.updateNotification(_serviceState.value)` is invoked on every single card progress increment.
   - **Impact:** Re-inflates custom `RemoteViews` layouts (`notification_custom_test.xml`) dozens of times per second, triggering OS notification throttling warnings and CPU overhead.

2. **Full WebView Page Reloads per Card (`TestService.kt` lines 593–600):**
   - **Mechanism:** Rather than resetting form fields via JavaScript, `TestService` executes:
     `webView.evaluateJavascript("document.body.innerHTML = '';", null)`
     `webView.clearHistory()`
     `webView.clearFormData()`
     `webView.loadUrl(url)`
   - **Impact:** Re-fetches the entire router HTML, CSS, and JS over the Wi-Fi link for every single card attempt with `LOAD_NO_CACHE` enabled.

3. **Unbounded Memory List Flow Mapping in `HomeViewModel.kt` (lines 118–130):**
   - **Mechanism:** `testResultRepository.getResultsBySession(latest.id)` is collected continuously. On every single card insert, Room emits the entire cumulative list of results. `toLogEntries()` and `toStatistics()` re-map the entire list in memory.
   - **Impact:** O(N^2) allocations over the life of a 1,000-card session.

### 7.3 Medium Performance Issues (MEDIUM)

1. **Unbounded WebView Pool Allocation (`TestService.kt` lines 322–359):**
   - Preference allows `thread_count` up to 100. Instantiating even 10 WebViews on mobile hardware exhausts RAM and Chromium renderer processes.
2. **Cache Disabled Globally (`TestService.kt` line 110):**
   - `WebSettings.LOAD_NO_CACHE` prevents caching of static router assets (images, stylesheets, scripts).
3. **RecyclerView inside NestedScrollView (`fragment_home.xml` lines 288–291):**
   - Fixed height 200dp inside `NestedScrollView` forces layout measure recalculations and breaks view recycling.

### 7.4 Low Performance Issues (LOW)

1. **`LogTerminalView.appendLog()` (lines 43–57):**
   - Reconstructs entire HTML string from 200 lines and invokes `Html.fromHtml()` on every log entry.

---

## 8. Test Engine Audit

### 8.1 Comparison of Test Strategies

The codebase contains three separate strategy implementations plus one default inline test method:
1. `MotasemTestStrategy.kt` (295 LOC)
2. `BelloTestStrategy.kt` (248 LOC)
3. `AbashaTestStrategy.kt` (251 LOC)
4. Inline `testCard()` in `TestService.kt` (lines 641–780)

#### Architectural Deficiencies:
- **Zero Polymorphism:** None of the strategies implement an interface or extend an abstract class. Each is an independent Kotlin `object` containing a single monolithic `suspend fun`.
- **90% Code Duplication:**
  - All three read `page_load_delay` and `card_test_delay` from SharedPreferences with the exact same boilerplate.
  - All three contain identical cookie and cache clearing logic.
  - All three run identical 20-retry `while` loops with `delay(1000)` waiting for DOM readiness.
  - All three contain nearly identical pre-flight logout checks, authorizing checks, and logout scripts.
  - Only minor selector strings differ (e.g., `#username` vs `#uname` vs `MikroTicket Status`).
- **Naive Strategy Selection (`TestService.kt` lines 631–639):**
  ```kotlin
  if (router.name.contains("معتصم", ignoreCase = true) || router.name.contains("motasem", ignoreCase = true)) {
      return testMotasemCard(...)
  }
  if (router.name.contains("بيلو", ignoreCase = true) || router.name.contains("bello", ignoreCase = true)) {
      return testBelloCard(...)
  }
  if (router.name.contains("اباشا", ignoreCase = true) || router.name.contains("abasha", ignoreCase = true) || router.name.contains("الباشا", ignoreCase = true)) {
      return testAbashaCard(...)
  }
  ```
  If a user creates a new profile for a Mikrotik router named "Home Router", the app falls back to the generic `testCard()` method which lacks specific Mikrotik redirect handling, rather than letting the user configure the router family or protocol profile!

### 8.2 Missing Architecture Components

To operate reliably and cleanly, the test engine requires:
1. **Polymorphic Strategy Interface (`RouterTestStrategy`):** Standardizes `testCard()`, `preFlightCheck()`, and `handleLogout()`.
2. **Engine State Machine (`TestEngineStateMachine`):** Explicit sealed states (`Idle`, `Initializing`, `TestingCard`, `AwaitingResult`, `LoggingOut`, `Paused`, `Error`, `Completed`) replacing arbitrary string states (`"RUNNING"`, `"PAUSED"`, `"LOAD_ERROR"`).
3. **WebView Controller & Pool Manager (`WebViewController`):** Decouples WebView lifecycle and thread safety from the Service.
4. **Result Evaluator (`ResultEvaluator`):** Replaces the empty `ResultChecker` object with reactive DOM observers.

---

## 9. Delay / Timing Audit

The codebase contains **66 hard-coded `delay(...)` calls**. The table below documents every single occurrence:

| File | Line | Delay | Why It Exists | Fixed/Adaptive | Problem | Recommendation |
|---|---|---|---|---|---|---|
| `SecurityViewModel.kt` | 52 | `1500ms` | Fake password verification animation | Fixed | Blocks user interaction artificially | Remove or replace with real crypto hash |
| `SecurityViewModel.kt` | 58 | `2000ms` | Delay before redirecting after success | Fixed | Unnecessary lag in navigation | Navigate immediately or reduce to 300ms |
| `AbashaTestStrategy.kt` | 53 | `300ms` | Wait after `stopLoading()` | Fixed | Arbitrary blind pause | Use WebViewClient callback |
| `AbashaTestStrategy.kt` | 57 | `2500ms` | Blind wait for page load | Fixed | Wastes 2.5s even if page loads in 100ms | Await `onPageFinished` via Deferred |
| `AbashaTestStrategy.kt` | 96 | `3000ms` | Wait for logout redirect | Fixed | Blind pause | Observe URL change or DOM readiness |
| `AbashaTestStrategy.kt` | 100 | `2500ms` | Wait after reloading login page | Fixed | Blind pause | Await `onPageFinished` |
| `AbashaTestStrategy.kt` | 111 | `1000ms` | Polling interval for form ready | Fixed | Inefficient polling | Use MutationObserver or JS Promise |
| `AbashaTestStrategy.kt` | 116 | `pageLoadDelay` (2000ms) | Extra user-configured wait | Fixed | Adds 2s to every card test | Eliminate; inject immediately when DOM ready |
| `AbashaTestStrategy.kt` | 170 | `cardTestDelay` (3000ms) | Wait for router auth response | Fixed | Adds 3s to every card test | Use DOM MutationObserver callback |
| `AbashaTestStrategy.kt` | 194 | `4000ms` | Wait if router reports "authorizing" | Fixed | 4s idle pause | Retry on reactive event |
| `AbashaTestStrategy.kt` | 208 | `2000ms` | Wait after clicking logout button | Fixed | 2s idle pause | Wait for redirect callback |
| `AbashaTestStrategy.kt` | 217 | `2500ms` | Wait if result state is "unknown" | Fixed | 2.5s idle pause | Bound with timeout, not blind delay |
| `AbashaTestStrategy.kt` | 238 | `2500ms` | Wait after successful logout | Fixed | 2.5s idle pause | Await login page redirect |
| `BelloTestStrategy.kt` | 53 | `300ms` | Wait after `stopLoading()` | Fixed | Arbitrary blind pause | Use WebViewClient callback |
| `BelloTestStrategy.kt` | 57 | `2500ms` | Blind wait for page load | Fixed | Wastes 2.5s | Await `onPageFinished` |
| `BelloTestStrategy.kt` | 95 | `3000ms` | Wait for logout redirect | Fixed | Blind pause | Observe URL change |
| `BelloTestStrategy.kt` | 99 | `2500ms` | Wait after reloading login page | Fixed | Blind pause | Await `onPageFinished` |
| `BelloTestStrategy.kt` | 110 | `1000ms` | Polling interval for form ready | Fixed | Inefficient polling | Use MutationObserver |
| `BelloTestStrategy.kt` | 115 | `pageLoadDelay` (2000ms) | Extra user-configured wait | Fixed | Adds 2s per card | Inject when DOM ready |
| `BelloTestStrategy.kt` | 169 | `cardTestDelay` (3000ms) | Wait for router auth response | Fixed | Adds 3s per card | Use DOM MutationObserver |
| `BelloTestStrategy.kt` | 193 | `4000ms` | Wait if router reports "authorizing" | Fixed | 4s idle pause | Reactive retry |
| `BelloTestStrategy.kt` | 206 | `2000ms` | Wait after clicking logout button | Fixed | 2s idle pause | Wait for redirect |
| `BelloTestStrategy.kt` | 214 | `2500ms` | Wait if result state is "unknown" | Fixed | 2.5s idle pause | Timeout-driven poll |
| `BelloTestStrategy.kt` | 235 | `2500ms` | Wait after successful logout | Fixed | 2.5s idle pause | Await login redirect |
| `MotasemTestStrategy.kt` | 57 | `300ms` | Wait after `stopLoading()` | Fixed | Arbitrary blind pause | Use WebViewClient callback |
| `MotasemTestStrategy.kt` | 64 | `2500ms` | Blind wait for page load | Fixed | Wastes 2.5s | Await `onPageFinished` |
| `MotasemTestStrategy.kt` | 107 | `3000ms` | Wait for logout redirect | Fixed | Blind pause | Observe URL change |
| `MotasemTestStrategy.kt` | 111 | `2500ms` | Wait after reloading login page | Fixed | Blind pause | Await `onPageFinished` |
| `MotasemTestStrategy.kt` | 122 | `1000ms` | Polling interval for form ready | Fixed | Inefficient polling | Use MutationObserver |
| `MotasemTestStrategy.kt` | 127 | `pageLoadDelay` (2000ms) | Extra user-configured wait | Fixed | Adds 2s per card | Inject when DOM ready |
| `MotasemTestStrategy.kt` | 209 | `cardTestDelay` (3500ms) | Wait for router auth response | Fixed | Adds 3.5s per card | Use DOM MutationObserver |
| `MotasemTestStrategy.kt` | 240 | `4000ms` | Wait if router reports "authorizing" | Fixed | 4s idle pause | Reactive retry |
| `MotasemTestStrategy.kt` | 253 | `2000ms` | Wait after clicking logout button | Fixed | 2s idle pause | Wait for redirect |
| `MotasemTestStrategy.kt` | 261 | `2500ms` | Wait if result state is "unknown" | Fixed | 2.5s idle pause | Timeout-driven poll |
| `MotasemTestStrategy.kt` | 282 | `2500ms` | Wait after successful logout | Fixed | 2.5s idle pause | Await login redirect |
| `TestService.kt` | 254 | `200ms` | Wait after `stopLoading()` in pool | Fixed | Arbitrary pause | Use callback |
| `TestService.kt` | 266 | `3000ms` | Wait before retrying failed load | Fixed | Inflexible backoff | Exponential backoff |
| `TestService.kt` | 271 | `1500ms` | Wait for scripts after page loaded | Fixed | Blind wait | Document readyState listener |
| `TestService.kt` | 280 | `2500ms` | Wait for logout to process | Fixed | Blind wait | Navigation listener |
| `TestService.kt` | 293 | `3000ms` | Wait before retrying reload | Fixed | Inflexible backoff | Exponential backoff |
| `TestService.kt` | 296 | `1000ms` | Wait after reload finishes | Fixed | Blind wait | Remove |
| `TestService.kt` | 375 | `2000ms` | Wait after initial pool preloading | Fixed | Blind wait | Await pool ready barrier |
| `TestService.kt` | 395 | `500ms` | Spin wait while paused or relogging | Fixed | Tight polling loop | Use Kotlin Mutex / Channel |
| `TestService.kt` | 427 | `3000ms` | Wait before retrying timed-out load | Fixed | Inflexible backoff | Exponential backoff |
| `TestService.kt` | 442 | `1500ms` | Wait for DOM stability check | Fixed | Blind wait | `document.readyState` check |
| `TestService.kt` | 465 | `1000ms` | Wait after clicking auto-reload button | Fixed | Blind wait | Deferred load listener |
| `TestService.kt` | 472 | `1000ms` | Wait for workers to pause on relogin | Fixed | Race condition risk | Use Coroutine Barrier/Mutex |
| `TestService.kt` | 476 | `3000ms` | Wait for global logout processing | Fixed | Blind wait | Navigation listener |
| `TestService.kt` | 495 | `2000ms` | Wait after reloading all WebViews | Fixed | Blind wait | Barrier `awaitAll` |
| `TestService.kt` | 500 | `500ms` | Polling loop waiting for relogin | Fixed | Busy-wait | Condition variable / Mutex |
| `TestService.kt` | 533 | `500ms` | Polling loop waiting for success lock | Fixed | Busy-wait | Channel or Mutex lock |
| `TestService.kt` | 544 | `delayMs` (2000ms) | Interrupted card backoff | Fixed | Artificial delay | Re-queue immediately |
| `TestService.kt` | 569 | `1000ms` | Wait before triggering logout | Fixed | Blind wait | Trigger logout immediately |
| `TestService.kt` | 573 | `4000ms` | Wait for logout to process | Fixed | Huge 4s freeze on success | Await logout response |
| `TestService.kt` | 602 | `delayMs` (2000ms) | Wait between successive cards | Fixed | Forces 2s minimum delay | Adaptive rate limiter |
| `TestService.kt` | 663 | `500ms` | Wait after `stopLoading()` | Fixed | Arbitrary pause | Remove |
| `TestService.kt` | 677 | `1000ms` | Wait after `loadUrl()` finishes | Fixed | Blind wait | Remove |
| `TestService.kt` | 728 | `2500ms` | Wait after clicking logout in form | Fixed | Blind wait | Navigation listener |
| `TestService.kt` | 731 | `2000ms` | Wait after login redirect click | Fixed | Blind wait | Navigation listener |
| `TestService.kt` | 733 | `1500ms` | Retry interval for form readiness | Fixed | Polling interval | MutationObserver |
| `TestService.kt` | 754 | `1000ms` | Polling interval for check result | Fixed | Polling interval | MutationObserver |
| `TestService.kt` | 765 | `2000ms` | Wait when router reports authorizing | Fixed | Blind wait | Reactive retry |
| `TestService.kt` | 789 | `delayMs` (2000ms) | Screenshot capture interval | Fixed | Main thread churn | Render on background or decouple |
| `TestService.kt` | 834 | `500ms` | Motasem pause condition polling | Fixed | Busy-wait | Flow/Mutex suspension |
| `TestService.kt` | 847 | `500ms` | Bello pause condition polling | Fixed | Busy-wait | Flow/Mutex suspension |
| `TestService.kt` | 860 | `500ms` | Abasha pause condition polling | Fixed | Busy-wait | Flow/Mutex suspension |

---

## 10. WebView Audit

### 10.1 Configuration & Settings

- **JavaScript Execution (`javaScriptEnabled = true`):** Enabled as required for form interaction and DOM inspection.
- **DOM Storage (`domStorageEnabled = true`):** Enabled for HTML5 router portals.
- **Cache Policy (`LOAD_NO_CACHE`):** Disables cache universally. Forces re-downloading static router bundles on every single page load.
- **Mixed Content (`MIXED_CONTENT_ALWAYS_ALLOW`):** Allows unencrypted HTTP assets inside HTTPS portals.
- **User Agent:** Fixed string `"Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"`. Does not identify real device characteristics.

### 10.2 SSL Certificate Handling (CRITICAL VULNERABILITY)

In `TestService.kt` lines 141–146:
```kotlin
override fun onReceivedSslError(
    view: WebView?, handler: SslErrorHandler?, error: SslError?
) {
    Timber.d("Proceeding with self-signed SSL error: ${error?.primaryError}")
    handler?.proceed()
}
```
- **Severity: CRITICAL**
- **Analysis:** Google Play Store automatically flags and rejects applications that unconditionally call `handler?.proceed()` in `onReceivedSslError`. It completely disables TLS protection, allowing any rogue Wi-Fi access point or attacker to intercept router credentials and user data.
- **Recommendation:** Only permit specific router IP certificates if explicitly trusted by the user, or restrict bypass to local private IP subnets (`192.168.0.0/16`, `10.0.0.0/8`, `172.16.0.0/12`) with explicit user consent.

### 10.3 JavaScript Injection Security & Reliability

In `InjectionManager.kt` lines 14–21:
- Selectors and card codes are injected into JavaScript strings using basic string template substitution.
- While `JSONObject.quote(card)` is used for card numbers, selector strings (`usernameSel`, `passwordSel`, `submitSel`) are escaped only with `.replace("'", "\\'")`. If a selector contains special CSS characters, backslashes, or quotes, the script syntax breaks silently.
- In `RouterProfileEntity.kt`, there is a `customJs: String?` column in the database. If populated, executing arbitrary JS in the WebView context without strict sandboxing presents a script execution vulnerability.

---

## 11. Security Audit

| Issue ID | Category | Severity | File & Line | Finding & Vulnerability Evidence | Impact |
|---|---|---|---|---|---|
| **SEC-01** | Credentials | **CRITICAL** | `SecurityViewModel.kt:54` | `if (password == "MOHAMED564")` — Master application unlock password hard-coded in plaintext. | Anyone can extract the password by decompiling the APK with `jadx` or `strings`. |
| **SEC-02** | Network / TLS | **CRITICAL** | `TestService.kt:145` | `handler?.proceed()` unconditionally bypasses all SSL certificate errors. | MitM attacks; Play Store rejection. |
| **SEC-03** | Network | **HIGH** | `network_security_config.xml:4` | `<base-config cleartextTrafficPermitted="true" />` allows unencrypted cleartext HTTP traffic app-wide. | Traffic can be intercepted on public or hostile Wi-Fi networks. |
| **SEC-04** | Data Leakage | **HIGH** | `file_provider_paths.xml:4` | `<files-path name="logs" path="." />` exposes entire `context.filesDir` root via FileProvider. | Any app with granted URI permission can traverse and read all internal private app files. |
| **SEC-05** | Backup / Auth Bypass | **HIGH** | `backup_rules.xml:3` & `data_extraction_rules.xml:4` | `<include domain="sharedpref" path="." />` backs up all SharedPreferences including unlock status and failed attempts. | Attacker can dump or edit backup files to bypass 3-attempt security lockout. |
| **SEC-06** | Data Storage | **HIGH** | `RouterProfileEntity.kt:18` | `@ColumnInfo(name = "password") val password: String = ""` stored in plaintext in SQLite. | Router admin credentials stored without encryption. |
| **SEC-07** | Cryptography | **MEDIUM** | `SecurityUtils.kt:24-30` | Uses deprecated `MasterKeys` and `EncryptedSharedPreferences.create()`. Returns plaintext on failure. | Compatibility failure on newer Android versions; fallback exposes plain passwords. |
| **SEC-08** | Secrets | **MEDIUM** | Root directory | `debug.keystore` and `debug.keystore.base64` are checked into source control. | Exposes private signing key and keystore password (`android`). |

---

## 12. Database Audit

### 12.1 Schema & Versioning

- **Database Name:** `wdmaster_db` (`AppDatabase.DATABASE_NAME`)
- **Current Version:** `4`
- **Schema Export:** `exportSchema = false` (Room schemas are not exported to project version control).
- **Destructive Migration Risk (CRITICAL):**
  - In `AppDatabase.kt` line 32: Only `MIGRATION_1_2` is implemented.
  - In `AppModule.kt` line 36: `.fallbackToDestructiveMigration()` is active.
  - **Impact:** Upgrading any existing user from Database version 1, 2, or 3 to version 4 will completely drop all SQLite tables, erasing all router configurations, generated cards, test sessions, and historical records.

### 12.2 Missing Indexes and Foreign Keys

| Entity | Field | Missing Feature | Impact |
|---|---|---|---|
| `TestResultEntity` | `sessionId` | No Foreign Key to `test_sessions(id)` | Orphaned records if sessions are deleted manually. |
| `TestResultEntity` | `sessionId` | No Index on `sessionId` | Full table scan on every query in `getResultsBySession()`. |
| `TestResultEntity` | `testedAt` | No Index on `testedAt` | Full table scan on `ORDER BY testedAt DESC`. |
| `TestSessionEntity` | `routerId` | No Index on `routerId` | Full table scan when filtering sessions by router. |
| `CardEntity` | `code` | No Unique Index on `code` | Allows duplicate cards to be inserted into the database. |

### 12.3 Excessive Sequential Writes

In `TestService.kt` (lines 548–590), every tested card performs:
1. `testResultRepository.insertResult(TestResultEntity(...))`
2. `sessionRepository.updateCounts(sessionId, success, failure)`
Testing 500 cards executes 1,000 separate disk transactions. A batch buffer (`insertResults`) should be used instead.

---

## 13. Memory Leak Audit

1. **WebView Native Allocation Leaks (`TestService.kt` lines 82, 350–358, 882–886):**
   - WebViews are created using `applicationContext` and placed in `webViewPool`.
   - The pool size can be configured up to 100 via preferences (`thread_count`).
   - If `TestService` is killed or interrupted abnormally without reaching `onDestroy()`, native Chromium renderers and hardware layers may leak.
2. **Continuous Screenshot Bitmap Allocation (`TestService.kt` lines 783–806):**
   - Creates a new `Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)` every 2,000 ms.
   - Encodes to JPEG into a new `ByteArrayOutputStream` on every tick.
   - Pushes raw byte arrays into `ServiceState` StateFlow, retaining images in memory and causing continuous GC churn.
3. **Decoded Bitmaps in Fragment (`TestFragment.kt` lines 132–150):**
   - Decodes JPEG byte arrays into Bitmaps in `viewLifecycleOwner.lifecycleScope` without recycling or caching the previous `Bitmap`.
4. **Android Context Retained in ViewModels:**
   - `HomeViewModel.startMonitoringConnection(context: Context)` retains `ConnectivityManager` and context references.
   - `HistoryViewModel.exportToFile(context: Context, ...)` receives Activity context.
5. **Static Service Instance Exposure (`ServiceBinder.kt` line 6):**
   - `fun getService(): TestService = service` provides clients with a strong reference to the entire Service, preventing garbage collection if bound by active components.

---

## 14. UI Audit

1. **ViewBinding Bypassed in Favor of `findViewById`:**
   - In `app/build.gradle.kts`: `buildFeatures { viewBinding = true }`.
   - In generated outputs: `ActivityMainBinding`, `FragmentHomeBinding`, `FragmentTestBinding`, etc., are successfully compiled.
   - In code: `MainActivity`, `HomeFragment`, `TestFragment`, `SecurityFragment`, `LockedFragment`, `RouterManagerFragment`, and `RouterFormFragment` all manually call `findViewById` on dozens of views.
2. **Scroll Conflict in HomeFragment (`fragment_home.xml` lines 288–291):**
   - An inner `RecyclerView` (`recycler_log`) with a fixed height of `200dp` is placed inside an outer `NestedScrollView`. This breaks standard view recycling and causes nested scrolling friction.
3. **Deep View Hierarchy & Overdraw:**
   - Layouts feature 5+ levels of nesting (`NestedScrollView → LinearLayout → MaterialCardView → LinearLayout → RelativeLayout → LinearLayout`).
4. **Hardcoded Form Values in XML Layouts:**
   - In `fragment_router_form.xml`:
     `android:text="192.168.1.1"`
     `android:text="/login"`
     `android:text="input[name='username']"`
     `android:text="input[name='password']"`
     `android:text="input[type='submit']"`
     These should be `android:hint` or set dynamically, not hard-coded pre-filled text in XML.
5. **Dead Layouts in Resources:**
   - `res/layout/dialog_router_form.xml` is never inflated.
   - `res/layout/view_log_terminal.xml` is never inflated.

---

## 15. Localization Audit

### 15.1 Missing Localization Strings in English

- `values/strings.xml` (Default): **169 lines**
- `values-ar/strings.xml` (Arabic): **169 lines**
- `values-en/strings.xml` (English): **57 lines**
- **Defect:** `values-en/strings.xml` is missing **over 110 string resources**, including all strings from `auto_string_1` through `auto_string_53`, delay preferences, security messages, and router hints.

### 15.2 Hard-coded Strings in Kotlin Code

| File | Line | Hard-coded String | Correct Solution |
|---|---|---|---|
| `HomeViewModel.kt` | 222 | `"الرجاء اختيار راوتر أولاً"` | Extract to `@string/error_select_router` |
| `HomeViewModel.kt` | 236 | `"فشل توليد البطاقات"` | Extract to `@string/error_generate_cards` |
| `HomeViewModel.kt` | 242 | `"حدث خطأ أثناء البدء: ${e.message}"` | Extract to formatted string resource |
| `HomeFragment.kt` | 133–134 | `"تنبيه: غير متصل بالواي فاي"`, message text | Extract to string resources |
| `HomeFragment.kt` | 144–145 | `"عملية جارية بالفعل"`, message text | Extract to string resources |
| `HomeFragment.kt` | 195–196 | `"تأكيد إلغاء الفحص الجاري"`, message text | Extract to string resources |
| `HomeFragment.kt` | 213–214 | `"تأكيد حذف السجلات"`, message text | Extract to string resources |
| `HomeFragment.kt` | 290–292 | `"الإجمالي المولد"`, `"البطاقات الناجحة"`, `"البطاقات الفاشلة"` | Extract to string resources |
| `TestFragment.kt` | 106–111 | `"حالة الاختبار: جاري الفحص النشط..."`, etc. | Extract to string resources |
| `TestFragment.kt` | 114 | `"البطاقة الحالية للفحص: ..."` | Extract to formatted string resource |
| `TestFragment.kt` | 125 | `"استئناف"`, `"إيقاف مؤقت"` | Use `@string/notification_resume` / pause |
| `TestFragment.kt` | 157 | `"اكتمل الاختبار الجاري بنجاح!"` | Extract to string resource |
| `TestFragment.kt` | 192–195 | `"فشل تحميل الصفحة"`, etc. | Extract to string resources |
| `HistoryViewModel.kt` | 73–74 | `"تم تصدير النتائج بنجاح إلى: ..."`, error text | Extract to formatted string resources |
| `SettingsFragment.kt` | 81, 85, 92 | Dialog button labels and toast message | Extract to string resources |
| `RouterManagerFragment.kt`| 94–99 | `"حذف ملف التعريف"`, confirmation text | Extract to string resources |
| `RouterFormFragment.kt` | 82 | `"إضافة راوتر مستهدف جديد"`, `"تعديل بيانات الراوتر"` | Extract to string resources |
| `RouterFormFragment.kt` | 153, 180 | Toast validation and success messages | Extract to string resources |
| `SessionAdapter.kt` | 37, 40 | `"Session #${item.id}"`, `"Success: ... \| Failure: ..."` | Extract to formatted string resources |
| `nav_graph.xml` | 34, 48, 68, 74, 84, 90 | Destination labels in Arabic | Use `@string/...` references |

### 15.3 Desynchronized Preferences Storage

- `LocaleHelper.kt` stores language in `SharedPreferences("locale_prefs")`.
- `AppPreferences.kt` stores language in `DataStore("app_settings")`.
- `SettingsFragment.kt` reads/writes language in `PreferenceManager.getDefaultSharedPreferences()`.
Changing language in one settings component does not synchronize across the others.

---

## 16. Architecture Red Flags

```
================================================================================
                           ARCHITECTURE RED FLAGS
================================================================================
[RED FLAG 01] GOD SERVICE: TestService.kt (893 LOC) combines 8 distinct
              responsibilities (Foreground service, Notification updates,
              WebView pooling, JS injection, Channel worker pool, StateFlow
              mutations, SQLite writes per card, and continuous Screenshot
              rendering/compression).
[RED FLAG 02] LEAKED ENTITIES ACROSS LAYERS: All 5 Domain Repository interfaces
              (ICardRepository, IRouterRepository, ISessionRepository,
              ITestResultRepository, IPatternRepository) return Room Entity
              types from com.example.data.local.entity, completely breaking
              Domain layer isolation.
[RED FLAG 03] DEAD MAPPERS & MODELS: CardMapper, RouterMapper, and SessionMapper
              are 100% unused dead code because entities leak directly into
              the presentation layer.
[RED FLAG 04] HOLLOWED OUT RESULT CHECKER: ResultChecker.kt is an empty object
              stub with zero code.
[RED FLAG 05] PSEUDO-STRATEGY PATTERN: AbashaTestStrategy, BelloTestStrategy, and
              MotasemTestStrategy are static objects with 90% duplicated code,
              dispatched via router name string matching inside TestService.
[RED FLAG 06] GLOBAL STATIC STATE: TestService.isRunning and TestService.serviceState
              are static companion StateFlows observed directly by ViewModels
              and Activities, bypassing DI and dependency inversion.
[RED FLAG 07] TRIPLED PREFERENCE STORES: Preferences are split across
              AppPreferences (DataStore), ThemePreferences (DataStore), and
              3 separate SharedPreferences files ("locale_prefs", "theme_prefs",
              and default SharedPreferences).
[RED FLAG 08] MAIN THREAD RUNBLOCKING: MainActivity.kt executes runBlocking { }
              in onCreate() to read preferences synchronously on the UI thread.
[RED FLAG 09] DESTRUCTIVE MIGRATION TIME BOMB: Room DB is version 4 with only
              MIGRATION_1_2 provided. fallbackToDestructiveMigration() wipes all
              tables on version upgrades.
[RED FLAG 10] INTENT TRANSACTION TOO LARGE RISK: HomeFragment passes full card
              lists via Intent extras and NavController bundles, risking
              TransactionTooLargeException crashes.
[RED FLAG 11] FAKE UNIT TEST SCRIPT: FileProcessorTest.kt is a resource mutation
              script that modifies XML files during gradle test runs, rather than
              a unit test suite.
================================================================================
```

---

## 17. Complete Dependency Graph

### 17.1 Navigation and Presentation Graph

```
[MainActivity]
      │
      ├─► [NavHost: nav_graph.xml]
      │         │
      │         ├─► [SecurityFragment] ◄──► [SecurityViewModel] ──► [AppPreferences (DataStore)]
      │         │         │
      │         │         ├─► [LockedFragment]
      │         │         └─► [HomeFragment]
      │         │
      │         ├─► [HomeFragment] ◄──► [HomeViewModel]
      │         │         │                 ├─► [GenerateCardsUseCase] ──► [ICardRepository] ──► [CardDao]
      │         │         │                 ├─► [ManageRoutersUseCase] ──► [IRouterRepository] ──► [RouterProfileDao]
      │         │         │                 ├─► [ISessionRepository] ──► [SessionDao]
      │         │         │                 ├─► [ITestResultRepository] ──► [TestResultDao]
      │         │         │                 └─► [AppPreferences]
      │         │         │
      │         │         └─► Navigates to [TestFragment] via Bundle
      │         │
      │         ├─► [TestFragment] ◄──► [TestViewModel]
      │         │                           ├─► static TestService.serviceState
      │         │                           └─► static TestService.isRunning
      │         │
      │         ├─► [HistoryFragment] ◄──► [HistoryViewModel]
      │         │                               ├─► [ISessionRepository] ──► [SessionDao]
      │         │                               ├─► [ITestResultRepository] ──► [TestResultDao]
      │         │                               └─► [ExportResultsUseCase] ──► [ITestResultRepository]
      │         │
      │         ├─► [SettingsFragment] ◄──► [SettingsViewModel]
      │         │         │                     ├─► [AppPreferences]
      │         │         │                     ├─► [ThemePreferences]
      │         │         │                     ├─► [ISessionRepository]
      │         │         │                     └─► [ITestResultRepository]
      │         │         │
      │         │         └─► [RouterManagerFragment] ◄──► [RouterManagerViewModel]
      │         │                   │                             └─► [ManageRoutersUseCase]
      │         │                   └─► [RouterFormFragment] ◄──► [RouterManagerViewModel]
      │
      └─► [Koin Service Locator: appModule + viewModelModule]
```

### 17.2 Test Execution and Background Engine Graph

```
[HomeFragment]
      │ (Intent with EXTRA_ROUTER_ID, EXTRA_CARD_LIST, EXTRA_DELAY_MS)
      ▼
[TestService] (Foreground Service)
      │
      ├─► [NotificationHelper] ──► [RemoteViews: notification_custom_test.xml]
      │
      ├─► [Screenshot Coroutine Loop]
      │         └─► WebView.draw(Canvas) ──► JPEG compress ──► ServiceState.screenshotBytes
      │
      ├─► [WebView Pool Manager] (1 to N WebViews created in Service)
      │         │
      │         ▼
      ├─► [Channel: cardQueue] ──► [Worker Coroutines]
      │                                 │
      │       ┌─────────────────────────┴────────────────────────┐
      │       │ String-match Router Profile Name                 │
      │       ▼                                                  ▼
      │  [MotasemTestStrategy] / [BelloTestStrategy] / [AbashaTestStrategy] / [Inline]
      │       │
      │       ├─► [InjectionManager.buildInjectionJs()]
      │       ├─► [WebView.evaluateJavascript()]
      │       ├─► [InjectionManager.buildCheckResultJs()]
      │       └─► [InjectionManager.buildLogoutJs()]
      │
      ├─► [TestResultRepository.insertResult()] ──► [Room: test_results]
      ├─► [SessionRepository.updateCounts()]     ──► [Room: test_sessions]
      └─► [NotificationHelper.updateNotification()]
```

---

## 18. Complete Issue Register

| ID | Category | File | Line | Problem | Severity | Impact |
|---|---|---|---|---|---|---|
| **ISS-01** | Build | `gradle/wrapper/gradle-wrapper.jar` | N/A | Corrupted JAR file (`ZipException: zip END header not found`). | **P0** | Cannot execute `./gradlew`. |
| **ISS-02** | Security | `presentation/security/SecurityViewModel.kt` | 54 | Hard-coded unlock password (`"MOHAMED564"`). | **P1** | Complete bypass of lock protection. |
| **ISS-03** | Security | `service/TestService.kt` | 145 | `handler?.proceed()` unconditionally approves all SSL errors. | **P1** | MitM vulnerability; Play Store rejection. |
| **ISS-04** | Performance | `service/TestService.kt` | 783–826 | Continuous Main Thread Canvas drawing, JPEG compression, and StateFlow byte allocation. | **P1** | Severe UI stutter and memory churn. |
| **ISS-05** | Performance | Entire codebase | 66 locations | 66 hard-coded `delay(...)` calls causing massive idle time per card. | **P1** | Test loop is 10x–50x slower than required. |
| **ISS-06** | Database | `data/local/database/AppDatabase.kt` | 18, 32 | Room DB version 4 with only `MIGRATION_1_2` + `fallbackToDestructiveMigration()`. | **P1** | Silent deletion of user data on upgrade. |
| **ISS-07** | Performance | `service/TestService.kt` | 548–590 | 2 synchronous SQLite transactions per individual card test. | **P1** | Disk I/O bottleneck. |
| **ISS-08** | Security | `res/xml/network_security_config.xml` | 4 | `<base-config cleartextTrafficPermitted="true" />` enables global cleartext HTTP. | **P2** | Unencrypted traffic across entire app. |
| **ISS-09** | Security | `res/xml/file_provider_paths.xml` | 4 | `<files-path name="logs" path="." />` exposes app's root internal files directory. | **P2** | Data leakage risk via FileProvider. |
| **ISS-10** | Security | `res/xml/backup_rules.xml` | 3 | Full backup includes all SharedPreferences. | **P2** | ADB backup can extract credentials & bypass lockout. |
| **ISS-11** | Security | `data/local/entity/RouterProfileEntity.kt`| 18 | Router passwords stored in plaintext in Room DB. | **P2** | Unencrypted credentials. |
| **ISS-12** | Architecture | `service/TestService.kt` | 1–893 | God Service with 8 disparate responsibilities. | **P2** | Fragile, unmaintainable testing core. |
| **ISS-13** | Architecture | All 5 Domain Repositories | N/A | Interfaces return Room Entities directly, leaking Data layer into Domain. | **P2** | Breaks Clean Architecture isolation. |
| **ISS-14** | Architecture | `data/mapper/*` | N/A | `CardMapper`, `RouterMapper`, `SessionMapper` are dead code. | **P2** | Unused dead classes. |
| **ISS-15** | Architecture | `service/ResultChecker.kt` | 1–8 | Empty object stub with zero logic. | **P2** | Dead code stub. |
| **ISS-16** | Code Quality | `service/AbashaTestStrategy.kt` & others | 28 | Unsafe `!!` null assertion on nullable `webView`. | **P2** | Potential NullPointerException crash. |
| **ISS-17** | Code Quality | `presentation/MainActivity.kt` | 100–101 | `runBlocking` in `onCreate()` on the Main Thread. | **P2** | ANR risk on slow cold starts. |
| **ISS-18** | Concurrency | `util/DateUtils.kt` | 8–10 | Static `SimpleDateFormat` instances shared across threads. | **P2** | Race conditions & date format crashes. |
| **ISS-19** | Performance | `HomeViewModel.kt` | 118–130 | Continuous full-list re-query and re-mapping in memory per card. | **P2** | Memory churn on large test runs. |
| **ISS-20** | Performance | `service/TestService.kt` | 593–600 | Full WebView page reload (`loadUrl`) per card instead of form reset. | **P2** | 10x network overhead per card. |
| **ISS-21** | Performance | `service/NotificationHelper.kt` | 167–178 | Unthrottled custom RemoteViews notification post per card. | **P2** | Notification manager rate limiting warnings. |
| **ISS-22** | Performance | `service/TestService.kt` | 323, 354 | Thread count allows up to 100 concurrent WebViews. | **P2** | Guaranteed OutOfMemoryError on high thread count. |
| **ISS-23** | Performance | `service/TestService.kt` | 110 | `WebSettings.LOAD_NO_CACHE` disables all HTTP caching. | **P3** | Multiplies network transfer overhead. |
| **ISS-24** | Localization | `res/values-en/strings.xml` | 1–57 | 112 strings missing compared to Arabic & default `strings.xml`. | **P3** | Incomplete English localization. |
| **ISS-25** | Localization | Multiple Kotlin files | Multiple | User-facing copy hardcoded in Arabic and English in Kotlin code. | **P3** | Text cannot be localized dynamically. |
| **ISS-26** | UI | Multiple Fragments | Multiple | ViewBinding enabled in Gradle but ignored; manual `findViewById` used. | **P3** | Inefficient view lookup & null unsafety. |
| **ISS-27** | UI | `res/layout/fragment_home.xml` | 288–291 | `RecyclerView` inside `NestedScrollView` with fixed 200dp height. | **P3** | Scrolling conflicts and layout measurement thrashing. |
| **ISS-28** | Storage | `AppPreferences` vs `ThemePreferences` | Multiple | Tripled desynchronized preference storage mechanisms. | **P3** | Inconsistent settings persistence. |
| **ISS-29** | Test Suite | `test/java/FileProcessorTest.kt` | 1–127 | Fake unit test modifies resources on disk during test execution. | **P3** | Corrupts resource files during build runs. |
| **ISS-30** | Dependencies | `libs.versions.toml` | Multiple | 7 unused dependencies (`constraintlayout`, `swiperefreshlayout`, etc.).| **P4** | Unnecessary APK size and dependency bloat. |

---

## 19. Prioritized Problem List (P0 / P1 / P2 / P3 / P4)

### P0 — BLOCKER

#### **PROB-01: Corrupted Gradle Wrapper JAR**
- **Category:** Build System
- **File:** `gradle/wrapper/gradle-wrapper.jar`
- **Line:** N/A (Binary file)
- **Problem:** Corrupted ZIP header (`ZipException: zip END header not found`). Standard `./gradlew` execution fails immediately with code 1.
- **Evidence:** `jar tf gradle/wrapper/gradle-wrapper.jar` throws `java.util.zip.ZipException: zip END header not found`.
- **Impact:** Any developer, CI runner, or build tool attempting to run `./gradlew` fails completely.
- **Risk:** High; prevents standard build and test workflows.
- **Recommended Solution:** Re-generate a valid `gradle-wrapper.jar` matching Gradle 9.3.1 using `gradle wrapper --gradle-version 9.3.1`.
- **Dependencies:** None.
- **Estimated Complexity:** Low (Single file replacement).

---

### P1 — CRITICAL

#### **PROB-02: Hard-Coded Master Unlock Password**
- **Category:** Security
- **File:** `app/src/main/java/com/example/presentation/security/SecurityViewModel.kt`
- **Line:** 54
- **Problem:** Hard-coded plaintext string: `if (password == "MOHAMED564")`.
- **Evidence:** `SecurityViewModel.kt:54: if (password == "MOHAMED564")`.
- **Impact:** Any user can extract the password by opening the compiled APK with a decompiler.
- **Risk:** Complete failure of access control.
- **Recommended Solution:** Replace with PBKDF2/Argon2 password hash stored in secure Keystore storage, or implement proper authorization flow.
- **Dependencies:** `SecurityUtils`.
- **Estimated Complexity:** Medium.

#### **PROB-03: WebView Unconditional SSL Certificate Error Bypass**
- **Category:** Security
- **File:** `app/src/main/java/com/example/service/TestService.kt`
- **Line:** 145
- **Problem:** `handler?.proceed()` unconditionally approves all SSL errors in `onReceivedSslError()`.
- **Evidence:** `TestService.kt:145: handler?.proceed()`.
- **Impact:** Susceptible to Man-in-the-Middle attacks. Automatic rejection by Google Play Store security scanners.
- **Risk:** Critical security vulnerability.
- **Recommended Solution:** Restrict certificate bypass exclusively to RFC 1918 private IP addresses with explicit user confirmation, or validate against known router certificate pins.
- **Dependencies:** `TestService`.
- **Estimated Complexity:** Medium.

#### **PROB-04: Main Thread Canvas Screenshot & StateFlow Byte Flooding**
- **Category:** Performance & Memory
- **File:** `app/src/main/java/com/example/service/TestService.kt` & `presentation/test/TestFragment.kt`
- **Line:** `TestService.kt:783–826`, `TestFragment.kt:132–150`
- **Problem:** Redraws active WebView to Bitmap on Main Thread every 2s, compresses to JPEG, puts byte array in StateFlow, and decodes in Fragment.
- **Evidence:** Continuous bitmap allocation and decompression causing major GC pauses.
- **Impact:** Severe frame drops, UI freezes, battery drain, OutOfMemoryError.
- **Risk:** App crash on devices with < 4GB RAM.
- **Recommended Solution:** Decouple preview stream; capture surface only when Fragment is active; use PixelCopy API on background thread; pass reusable bitmap buffers.
- **Dependencies:** `TestService`, `TestFragment`.
- **Estimated Complexity:** High.

#### **PROB-05: 66 Hard-Coded `delay()` Calls Crippling Test Performance**
- **Category:** Performance
- **File:** `AbashaTestStrategy.kt`, `BelloTestStrategy.kt`, `MotasemTestStrategy.kt`, `TestService.kt`
- **Line:** 66 locations
- **Problem:** Fixed pauses of 2500ms, 3000ms, 4000ms, 1000ms per test cycle.
- **Evidence:** A single card test spends 6.5s–12s in pure idle coroutine sleep.
- **Impact:** 1,000 cards take > 2 hours to test.
- **Risk:** User abandonment due to sluggish test execution.
- **Recommended Solution:** Transition from polling/sleeping to reactive DOM MutationObservers and JavaScript Promise callbacks with timeouts.
- **Dependencies:** Strategy classes, `InjectionManager`.
- **Estimated Complexity:** High.

#### **PROB-06: Database Version 4 Missing Migrations with Destructive Fallback**
- **Category:** Database Integrity
- **File:** `app/src/main/java/com/example/data/local/database/AppDatabase.kt` & `di/AppModule.kt`
- **Line:** `AppDatabase.kt:18, 32`, `AppModule.kt:36`
- **Problem:** Database version is 4, but only `MIGRATION_1_2` exists. `.fallbackToDestructiveMigration()` is active.
- **Evidence:** Upgrading from DB version 2 or 3 triggers complete data destruction.
- **Impact:** Loss of all user router profiles, sessions, and test history on app upgrade.
- **Risk:** Irreversible data loss.
- **Recommended Solution:** Provide explicit migrations `MIGRATION_2_3`, `MIGRATION_3_4`, or comprehensive `MIGRATION_1_4`. Set `exportSchema = true`.
- **Dependencies:** `AppDatabase`, `AppModule`.
- **Estimated Complexity:** Medium.

---

### P2 — HIGH

#### **PROB-07: God Service `TestService.kt` (893 LOC)**
- **Category:** Architecture
- **File:** `app/src/main/java/com/example/service/TestService.kt`
- **Line:** 1–893
- **Problem:** Violates SRP by coupling background service, notifications, WebView pool, test execution, database writes, and screenshotting.
- **Recommended Solution:** Extract into `TestEngineCoordinator`, `WebViewController`, `TestNotificationManager`, and `ResultRecorder`.

#### **PROB-08: Data Layer Room Entities Leaked Across Domain & Presentation**
- **Category:** Architecture
- **File:** All 5 Repository interfaces in `domain/repository/`
- **Line:** Multiple
- **Problem:** `ICardRepository`, `IRouterRepository`, `ISessionRepository`, `ITestResultRepository`, `IPatternRepository` return `*Entity` objects.
- **Recommended Solution:** Expose clean Domain models (`Card`, `RouterProfile`, `TestResult`, `TestSession`) from repositories; activate Mappers.

#### **PROB-09: Synchronous Database Writes per Card**
- **Category:** Performance & Database
- **File:** `app/src/main/java/com/example/service/TestService.kt`
- **Line:** 548–590
- **Problem:** Performs 2 sequential SQLite transactions for every single card.
- **Recommended Solution:** Batch results in memory and write to Room in chunks of 50 or on session flush.

#### **PROB-10: Thread-Unsafe `SimpleDateFormat` in Singleton `DateUtils`**
- **Category:** Concurrency / Stability
- **File:** `app/src/main/java/com/example/util/DateUtils.kt`
- **Line:** 8–10
- **Problem:** `SimpleDateFormat` is not thread-safe; calling it concurrently across background worker coroutines causes crashes.
- **Recommended Solution:** Replace with `java.time.format.DateTimeFormatter` (available on minSdk 26+).

#### **PROB-11: Main Thread `runBlocking` in `MainActivity.kt`**
- **Category:** Performance & Lifecycle
- **File:** `app/src/main/java/com/example/presentation/MainActivity.kt`
- **Line:** 100–101
- **Problem:** `runBlocking` called on the Main thread in `onCreate()` to read DataStore preferences.
- **Recommended Solution:** Handle lock state reactively via `SecurityViewModel` without blocking Activity creation.

---

### P3 — MEDIUM

#### **PROB-12: Missing English Localization Strings**
- **Category:** Localization
- **File:** `app/src/main/res/values-en/strings.xml`
- **Line:** 1–57
- **Problem:** Missing 112 strings present in default and Arabic resources.
- **Recommended Solution:** Sync all keys across `values/strings.xml`, `values-ar/strings.xml`, and `values-en/strings.xml`.

#### **PROB-13: Hard-Coded User-Facing Copy in Kotlin Files**
- **Category:** Localization
- **File:** Multiple ViewModels and Fragments
- **Line:** Over 20 locations
- **Problem:** Dialog titles, toast messages, and error descriptions written in Arabic/English directly in code.
- **Recommended Solution:** Extract all strings to `strings.xml`.

#### **PROB-14: ViewBinding Configured but Ignored in Favor of `findViewById`**
- **Category:** UI & Code Quality
- **File:** All Fragments
- **Line:** Multiple
- **Problem:** ViewBinding is active in Gradle, but Fragments manually call `findViewById` on dozens of views.
- **Recommended Solution:** Bind views using generated `*Binding` classes in Fragments.

#### **PROB-15: Scroll Conflict & Overdraw in HomeFragment**
- **Category:** UI & Performance
- **File:** `app/src/main/res/layout/fragment_home.xml`
- **Line:** 288–291
- **Problem:** `RecyclerView` with fixed 200dp height placed inside `NestedScrollView`.
- **Recommended Solution:** Refactor to a unified `RecyclerView` with multiple view types or flatten the layout hierarchy.

---

### P4 — LOW

#### **PROB-16: Unused Libraries and Dead Dependencies**
- **Category:** Dependencies
- **File:** `gradle/libs.versions.toml` & `app/build.gradle.kts`
- **Line:** Multiple
- **Problem:** 7 unused dependencies (`constraintlayout`, `swiperefreshlayout`, `coil`, `lottie`, `okhttp`, `webkit`, `lifecycle-livedata-ktx`).
- **Recommended Solution:** Remove unused dependencies to reduce APK size and dependency vulnerability surface.

#### **PROB-17: Dead Code Objects and Unused Layouts**
- **Category:** Code Quality
- **File:** `ResultChecker.kt`, `FileUtils.kt`, `ValidationUtils.kt`, `PermissionHelper.kt`, `LogTerminalView.kt`, `dialog_router_form.xml`, `view_log_terminal.xml`
- **Problem:** Dead classes and layouts left in codebase.
- **Recommended Solution:** Safely remove in future cleanup phase.

---

## 20. Recommended Refactor Order (Roadmap for Future Phases)

To modernize and repair the codebase without regression, refactoring should proceed in strict sequential order:

```
┌───────────────────────────────────────────────────────────────────────┐
│ PHASE 1: BUILD SYSTEM & INFRASTRUCTURE RESTORATION                   │
│   1. Restore valid gradle-wrapper.jar (Gradle 9.3.1).                 │
│   2. Remove deprecated package attribute from AndroidManifest.xml.    │
│   3. Prune unused dependencies from libs.versions.toml & build.gradle.│
└───────────────────────────────────┬───────────────────────────────────┘
                                    │
                                    ▼
┌───────────────────────────────────────────────────────────────────────┐
│ PHASE 2: SECURITY HARDENING                                           │
│   1. Remove hard-coded password "MOHAMED564" from SecurityViewModel. │
│   2. Restrict WebView SSL bypass in TestService (no blanket proceed). │
│   3. Restrict network_security_config.xml cleartext to LAN subnets.   │
│   4. Narrow FileProvider path from "." to dedicated subfolder.        │
│   5. Encrypt router passwords in Room DB using modern Crypto MasterKey│
│   6. Exclude sensitive preferences from Android full backup rules.    │
└───────────────────────────────────┬───────────────────────────────────┘
                                    │
                                    ▼
┌───────────────────────────────────────────────────────────────────────┐
│ PHASE 3: DATABASE SCHEMA & MIGRATION STABILIZATION                    │
│   1. Add missing Room migrations (1->2, 2->3, 3->4) & exportSchema.   │
│   2. Add database indexes on foreign keys (sessionId, routerId).      │
│   3. Implement batch write buffer in TestResultRepository.            │
└───────────────────────────────────┬───────────────────────────────────┘
                                    │
                                    ▼
┌───────────────────────────────────────────────────────────────────────┐
│ PHASE 4: CLEAN ARCHITECTURE & DOMAIN MODEL RESTORATION                │
│   1. Update I*Repository interfaces to return Domain Models.          │
│   2. Wire CardMapper, RouterMapper, SessionMapper, TestResultMapper.  │
│   3. Remove direct *Entity imports from Domain UseCases.              │
│   4. Consolidate preference storage to DataStore.                     │
└───────────────────────────────────┬───────────────────────────────────┘
                                    │
                                    ▼
┌───────────────────────────────────────────────────────────────────────┐
│ PHASE 5: TEST ENGINE ARCHITECTURAL REDESIGN & OPTIMIZATION            │
│   1. Deconstruct God Service (TestService.kt):                        │
│      - Extract WebViewController (lifecycle, pool, thread safety).    │
│      - Extract polymorphic RouterTestStrategy interface.              │
│      - Extract ResultEvaluator (replace dead ResultChecker).          │
│      - Extract StateMachine with sealed classes.                      │
│   2. Eliminate 66 hard-coded delay() calls:                           │
│      - Implement MutationObserver & Promise-based event injection.    │
│   3. Redesign Screenshot preview:                                     │
│      - Decouple from main loop; capture only when UI observing.       │
└───────────────────────────────────┬───────────────────────────────────┘
                                    │
                                    ▼
┌───────────────────────────────────────────────────────────────────────┐
│ PHASE 6: UI, LOCALIZATION & QUALITY OF LIFE                           │
│   1. Migrate Fragments from findViewById to ViewBinding.              │
│   2. Fix NestedScrollView + RecyclerView conflict in HomeFragment.    │
│   3. Synchronize values-en/strings.xml with Arabic & default keys.     │
│   4. Extract all hardcoded strings from Kotlin code to resources.     │
│   5. Replace DateUtils SimpleDateFormat with java.time.               │
└───────────────────────────────────────────────────────────────────────┘
```

---

## 21. Risks

1. **WebView Injection Timing Breakages:** If fixed `delay()` calls are removed without robust DOM mutation listeners, fast routers may respond before scripts attach or slow routers may timeout prematurely.
2. **Database Migration Failures:** Incorrectly written Room migrations for version 4 could crash existing installations if user databases are in an intermediate state.
3. **Router Protocol Compatibility:** Refactoring `AbashaTestStrategy`, `BelloTestStrategy`, and `MotasemTestStrategy` into a single polymorphic engine must preserve specific Mikrotik and portal form quirks (e.g., hidden `sendin` form, `openLogout()` calls, and `doLogin()` hooks).

---

## 22. What Must NOT Be Changed Yet

In strict compliance with Phase 0 rules:
1. **DO NOT** replace or patch `gradle/wrapper/gradle-wrapper.jar` in this phase.
2. **DO NOT** delete any unused dependencies (`lottie`, `okhttp`, `coil`, etc.) in `build.gradle.kts`.
3. **DO NOT** delete dead files (`ResultChecker.kt`, `RouterMapper.kt`, `FileUtils.kt`, etc.).
4. **DO NOT** modify `TestService.kt`, strategies, or any `delay()` calls.
5. **DO NOT** alter Room database entities, DAOs, or migrations.
6. **DO NOT** change UI layouts, bindings, or XML resources.
7. **DO NOT** alter password logic in `SecurityViewModel.kt`.

---

## 23. Baseline Build / Test Results

### 23.1 Gradle Test Execution via System Gradle

Command executed:
```bash
ANDROID_HOME=/opt/android/sdk ANDROID_SDK_ROOT=/opt/android/sdk /opt/gradle/gradle-9.3.1/bin/gradle test
```

Result:
- **Build Outcome:** `BUILD SUCCESSFUL in 27s`
- **Actionable Tasks:** 57 actionable tasks (36 executed, 21 up-to-date).
- **Test Tasks Executed:**
  - `:app:testDebugUnitTest`
  - `:app:testReleaseUnitTest`
- **Tests Evaluated:**
  - 1 test executed: `com.example.FileProcessorTest.extractStrings()` — Passed.
- **Build Environment Findings:**
  - Android SDK 34 and Build Tools 36.0.0 are functional.
  - Java 21 environment is operational.
  - Standard `./gradlew` execution failed due to corrupted wrapper JAR (P0 Blocker).

---

## 24. Final Recommendations

1. Address the P0 blocker (`gradle-wrapper.jar`) at the immediate outset of Phase 1 before any other task.
2. Treat security vulnerabilities (hard-coded password, SSL bypass, cleartext configuration) as the primary functional priority in Phase 2.
3. Treat the Test Engine deconstruction and delay elimination as the primary performance priority in Phase 5 to realize the app's full potential for high-speed hotspot auditing.

---

## PHASE 0 COMPLETION CHECKLIST

- [x] Entire project inspected
- [x] Build system inspected
- [x] Dependencies inspected
- [x] All major services inspected
- [x] Test engine inspected
- [x] WebView inspected
- [x] Database inspected
- [x] Security inspected
- [x] Performance inspected
- [x] Memory inspected
- [x] UI inspected
- [x] Localization inspected
- [x] Tests inspected
- [x] Build baseline recorded
- [x] All findings documented

---

## PHASE 0 STATUS

**COMPLETE_WITH_BLOCKERS**
*(Completed with one P0 Build Blocker: corrupted `gradle/wrapper/gradle-wrapper.jar` preventing standard wrapper execution)*
