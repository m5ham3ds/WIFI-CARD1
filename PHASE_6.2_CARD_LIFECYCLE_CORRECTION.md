# PHASE 6.2 — CARD LIFECYCLE, RESULT INTEGRITY & SESSION FLOW CORRECTION REPORT

## 1. Executive Summary

Phase 6 introduced performance and concurrency optimizations (WebView pool, preloading, parallel workers). However, real-world captive portal testing revealed critical correctness problems in the card lifecycle:
1. Cards advancing before results were finalized (racing asynchronous evaluateJavascript callbacks).
2. Registration/card submission buttons causing premature next-card transitions.
3. Skip of the login page after session logout, directly testing the subsequent card against an invalid or intermediate router page.
4. Portal message `"لا يمكن استعمال البطاقة في جهازين"` (and its variations) being misclassified as a failure rather than an immediate SUCCESS condition.

Phase 6.2 executed a forensic repair enforcing a strict architectural invariant:
```text
ONE CARD
  ↓
ONE COMPLETE LIFECYCLE
  ↓
ONE AUTHORITATIVE RESULT
  ↓
ONE CLEAN SESSION TRANSITION
  ↓
ONLY THEN NEXT CARD
```

---

## 2. Forensic Bug Reproduction & Root Cause Analysis

### Bug A: Card Skips Result
- **Root Cause**: In asynchronous worker coroutines, WebView callbacks and timer delays did not have a synchronization barrier. The feeder queue consumed the next card whenever a navigation or intermediate check completed, before the persistent storage and UI counter update concluded.
- **Correction**: Introduced `CardLifecycleState` state machine with a mandatory terminal barrier in `finalizeCardAttempt(...)`. Next card consumption is strictly gated on reaching `CardLifecycleState.READY_FOR_NEXT`.

### Bug B: Button Dispatches Advancing State Prematurely
- **Root Cause**: Direct clicks or UI events interacting with test execution controls or form elements bypassed engine coordination.
- **Correction**: UI buttons only dispatch Intent actions or ViewModel commands (`ACTION_START`, `ACTION_PAUSE`, `ACTION_RESUME`, `ACTION_CANCEL`, `ACTION_RETRY_LOAD`). All engine state transitions are authoritative and internal to `TestService`.

### Bug C: Post-Logout Login Page Skip
- **Root Cause**: After logging out a successful card, workers immediately proceeded to inject the next card into the WebView without verifying that the captive portal had fully redirected to the clean login form.
- **Correction**: Added `ensureFreshLoginPage(...)` which:
  1. Issues navigation with an incremented `NavigationToken` generation.
  2. Executes DOM sanitization (clearing inputs and hiding stale error badges).
  3. Uses router-strategy-specific verification (`strategy.verifyFreshLoginPage(...)`) to confirm the presence of the username/password input fields before any card is injected.

### Bug D: Portal Notification "لا يمكن استعمال البطاقة في جهازين" Misclassification
- **Root Cause**: The string contains `"لا يمكن"` which standard error matchers previously marked as a failure. However, in Syrian and regional captive portals (MikroTik, Bello, AlBasha, Motasem), this message indicates that the card credentials are valid and active on another device.
- **Correction**: `ResultChecker.isTwoDevicesSuccess(...)` and `ResultChecker.isSuccess(...)` check for this exact condition and its semantic variants first, treating it as an immediate authoritative `SUCCESS`. Router logout is bypassed for this outcome because an active session was not created on the current device.

---

## 3. Architecture & State Machine

### 3.1 Card Lifecycle States (`CardLifecycleState`)
- `IDLE`: Worker not currently testing.
- `QUEUED`: Card popped from queue.
- `NAVIGATING`: Initializing generation navigation.
- `LOGIN_PAGE_READY`: Fresh login form confirmed present.
- `SUBMITTING`: Credentials injected and submitted.
- `WAITING_RESULT`: Polling portal response, alerts, and redirection.
- `SUCCESS`: Valid login confirmed.
- `FAILURE`: Invalid credentials confirmed.
- `TIMEOUT`: Response deadline exceeded.
- `NETWORK_ERROR`: Connection or DNS failure.
- `ENGINE_ERROR`: Unhandled exception.
- `LOGOUT_REQUESTED`: Teardown of active portal lease initiated.
- `LOGOUT_CONFIRMED`: Active lease cleared.
- `RESETTING`: Purging DOM inputs and cached alerts.
- `READY_FOR_NEXT`: Invariant satisfied. Worker is allowed to pull next card.

### 3.2 Terminal Result Barrier
`finalizeCardAttempt(...)` enforces:
1. **Idempotency Protection**: `finalizedAttempts` set guards against duplicate finalizations for the same `(sessionId, cardIndex, workerId, attemptSequence)`.
2. **Authoritative Persistence First**: Results are inserted into Room SQLite via `testResultRepository.insertResult(...)` before any UI or counter mutations.
3. **Counter Integrity**: Atomic increments of progress, success, and failure counters.
4. **Targeted Session Teardown**: Logout executed only for `NORMAL_LOGIN_SUCCESS`; skipped for `TWO_DEVICES_SUCCESS`.
5. **Fresh Page Verification**: `ensureFreshLoginPage(...)` executed before returning `READY_FOR_NEXT`.

---

## 4. Verification & Test Suite

All 28 unit tests pass cleanly:
- `Phase62CardLifecycleTest`:
  - `testTwoDevicesNotificationTreatedAsImmediateSuccess`: Verifies exact phrases in DOM and JS alert.
  - `testTwoDevicesNotificationFromJsAlert`: Verifies alert interceptor for two-device warning.
  - `testTwoDevicesSemanticVariations`: Verifies semantic Arabic matching.
  - `testCardTestOutcomeFactoryMethods`: Verifies outcome data models.
  - `testOneCardOneLifecycleStrictBarrier`: Simulates concurrent racing and verifies exact 1:1 attempt-to-result mapping.
  - `testDuplicateFinalizationRejection`: Confirms idempotent finalization.
  - `testBypassRouterLogoutOnTwoDevicesSuccess`: Verifies no redundant logout calls.
  - `testFreshLoginPageVerificationAcrossAllStrategies`: Verifies Abasha, Motasem, Bello, and Generic fresh login checks.
  - `testStaleAlertProtectionAcrossGenerations`: Verifies token generation isolation.
  - `testAuthoritativeResultToLogEntriesMapping`: Verifies log level and message formatting.

Build Status:
- `BUILD SUCCESSFUL` across all compilation and unit test tasks.
