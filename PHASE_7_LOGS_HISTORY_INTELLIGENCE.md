# PHASE 7 — LOGS, HISTORY & RESULT INTELLIGENCE REPORT

## 1. Purpose & Core Principles

Phase 6 and Phase 6.2 hardened the card-testing engine with:
- Explicit card lifecycle states (`CardLifecycleState`)
- Terminal result barrier (`finalizeCardAttempt`)
- Navigation generation tokens (`NavigationToken`)
- Stale callback protection
- Fresh login page verification across all 4 router strategies
- Authoritative persistence in Room SQLite prior to UI mutations
- Immediate SUCCESS classification for `"لا يمكن استعمال البطاقة في جهازين"`

Phase 7 focuses on:
```text
RESULT STORAGE
+
LOG INTEGRITY
+
HISTORY
+
RESULT CLASSIFICATION
+
SESSION REPORTING
+
RESULT INTELLIGENCE
```

### Core Invariant
Every card attempt produces exactly **one** authoritative terminal result:
```text
Card
  ↓
CardAttempt
  ↓
Execution
  ↓
Terminal Outcome
  ↓
Persistent TestResult (Room DB)
  ↓
Session Aggregation
  ↓
History / Logs / Statistics
```
Guarantees:
- **No duplicate results**: Exactly 1 persistent result per attempt.
- **No multiple session counts**: Atomic counter updates synchronized with database state.

---

## 2. Authoritative Result Model

The result classification model distinguishes high-level categories and granular, understandable sub-reasons:

### 2.1 Result Categories (`ResultCategory`)
- `SUCCESS`: Valid credentials or active authenticated card.
- `FAILURE`: Rejected credentials or expired card.
- `TIMEOUT`: Network/router response deadline exceeded.
- `NETWORK_ERROR`: Connection loss or DNS resolution error.
- `ENGINE_ERROR`: Runtime or WebView exception.

### 2.2 Result Sub-Reasons (`ResultSubReason`)
- `NORMAL_LOGIN_SUCCESS`: Standard captive portal login succeeded.
- `TWO_DEVICES_SUCCESS`: Card credentials confirmed valid and active on another device.
- `INVALID_CARD`: Incorrect card number or password.
- `EXPIRED_CARD`: Card time or date quota elapsed.
- `INSUFFICIENT_BALANCE`: Card data/bandwidth balance exhausted.
- `PORTAL_REJECTED`: Server rejected login without standard error code.
- `NETWORK_TIMEOUT`: Captive portal request timed out.
- `DNS_ERROR`: Hostname resolution failed.
- `WEBVIEW_ERROR`: System WebView navigation failure.
- `JAVASCRIPT_ERROR`: Script execution error.
- `UNKNOWN`: Unclassified outcome.

---

## 3. Success Reason Logic

A successful test is distinguished by its `successReason`:
1. `NORMAL_LOGIN`: The card successfully logged in and created an active router lease. Router logout is executed to free the slot for subsequent tests.
2. `TWO_DEVICES_ACTIVE_CARD`: The captive portal responded with:
   `"لا يمكن استعمال البطاقة في جهازين"`
   or semantic variants (`"لا يمكن استخدام الكرت في جهازين"`, etc.).
   The card is valid and actively used. Router logout is bypassed since no new session lease was created on the testing device.

---

## 4. Failure Classification Heuristics

`ResultChecker.classifyFailureSubReason(...)` inspects captive portal DOM and text to identify failure root causes:
- `"منتهي"` / `"expired"` → `EXPIRED_CARD`
- `"نفذ الرصيد"` / `"رصيد غير كاف"` / `"insufficient"` → `INSUFFICIENT_BALANCE`
- `"غير صحيح"` / `"invalid"` / `"not found"` → `INVALID_CARD`
- `"رفض"` / `"rejected"` / `"unauthorized"` → `PORTAL_REJECTED`

---

## 5. Log Integrity & Formatting

In `TestResultMapper.toLogEntries(...)`:
- Sensitive card numbers are masked where appropriate (`maskCard(...)`).
- Log levels are mapped strictly:
  - `LogLevel.SUCCESS`: `[تسجيل دخول ناجح]` or `[صالحة - مستعملة بجهازين]`
  - `LogLevel.WARNING`: `[انتهاء المهلة]` or `[خطأ اتصال]`
  - `LogLevel.ERROR`: `[فشل - <سبب الفشل العربي>]`
- Timestamps and message details remain deterministic and chronological.

---

## 6. History & Extended Filtering

In `HistoryViewModel` and `HistoryFragment`:
- Filters supported:
  - `all`: All records in the session.
  - `success`: All valid cards (including concurrent device hits).
  - `two_devices`: Specifically cards with active concurrent sessions.
  - `failure`: Rejected, expired, or invalid cards.
  - `timeout`: Network errors and timeouts.
- Session-specific export: `ExportResultsUseCase` accepts an optional `sessionId` to export only the selected session's records rather than the entire database.

---

## 7. Session Reporting & Statistics Aggregation

`TestResultMapper.toStatistics(...)` aggregates:
- `total`: Total attempts evaluated.
- `success`: Total successful cards.
- `failure`: Total unsuccessful attempts.
- `successRate`: Percentage of successful cards.
- `normalSuccessCount`: Count of direct login successes.
- `twoDevicesSuccessCount`: Count of cards active on two devices.
- `timeoutCount`: Count of timed-out requests.
- `networkErrorCount`: Count of connection errors.
- `invalidCardCount`: Count of invalid credentials.
- `expiredCardCount`: Count of expired cards.
- `avgDurationMs`: Average response time per test in milliseconds.

In `HomeViewModel`:
- Finished session logs and statistics are retained on the home screen after test completion rather than being erased, allowing users to inspect final results.

---

## 8. Verification & Test Suite Summary

The test suite in `Phase7ResultIntelligenceTest` verifies:
1. `testCardTestOutcomeCategoriesAndSubReasons`: All category and sub-reason mappings.
2. `testTwoDevicesNotificationClassifiedWithSubReason`: Exact and semantic two-device detection.
3. `testFailureSubReasonClassification`: Accuracy across expired, empty balance, and invalid credentials.
4. `testEntitySubReasonAndDomainMapping`: Room entity to domain model mapping with sub-reasons.
5. `testLogEntriesFormattingAndLevels`: Proper log levels and Arabic prefixes.
6. `testStatisticsAggregationBreakdown`: Calculation of totals, sub-totals, and average test durations.
7. `testHistoryFilteringCriteria`: Filtering logic across all dimensions.
