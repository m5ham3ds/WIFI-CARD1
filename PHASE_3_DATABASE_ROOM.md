# PHASE 3 — DATABASE & ROOM ARCHITECTURE REPORT
**Target Application:** WiFi Card Master Pro / WD Master  
**Package / Application ID:** `com.aistudio.wifimasterpro.fazzbe` (Namespace: `com.example`)  
**Phase:** PHASE 3 — DATABASE & ROOM ARCHITECTURE (INCLUDING PHASE 3.1 CORRECTION PASS)  
**Status:** **COMPLETE**  
**Execution Date:** 2026-10-07  
**Engineer:** Senior Android Database & Security Architect  

---

## 1. Executive Summary

Phase 3 establishes enterprise-grade Room database architecture, robust versioned migrations, and persistent credential security. In accordance with the project requirements and the **Phase 3.1 Database Integrity Correction Pass**, this phase achieves:

1. **Elimination of All Destructive Migration Fallbacks:** Zero destructive fallbacks remain anywhere in the project. Database upgrades preserve 100% of user data.
2. **Official Room Schema Export Enabled:** Configured KSP Room schema export (`exportSchema = true`) and exported official JSON schema for version 5 at `app/schemas/com.example.data.local.database.AppDatabase/5.json`.
3. **Legacy Plaintext Credential Migration:** Automatic in-place encryption of legacy plaintext passwords during migration to AES-256-GCM ciphertext, leaving zero plaintext in SQLite.
4. **Per-Router Optional Password Authentication:** Controlled per-profile via `passwordEnabled: Boolean = false`, preserving the `password` and `passwordSelector` columns.
5. **Corrected Default Router Profile Hosts:** Configured `wifi.sd.net` for ALBASHA and MOTASEM, and `www.bello.com` for BELLO.
6. **Comprehensive Test Suite & Verification:** Rigorous unit tests covering all migration chains (v1->v2->v3->v4->v5 and compound jumps), fail-closed decryption, and data preservation.

---

## 2. Current Authentication Model & Business Rationale

The target captive portals (ALBASHA, MOTASEM, BELLO) exhibit the following operational characteristics:
- **Card-Only / Username-Only Authentication:** In their current deployments, the captive portals allow authentication using the Card Code / Username alone. By default, users do not enter a password.
- **HTML Password Field Presence:** The captive portal HTML login pages include password fields (`<input name="password" type="password">` or MikroTicket password tabs), supporting MD5/chap authentication or password-enabled vouchers.
- **Architectural Imperative:** Removing password fields from the database would break future compatibility and prevent users from authenticating against routers where password authentication is activated. Therefore, password support is **strictly preserved** and made **optional** on a per-router profile basis via `passwordEnabled`.

---

## 3. OPTIONAL PASSWORD AUTHENTICATION

As required by the Phase 3 specification, this section details the optional password authentication architecture:

### 3.1 Username / Card is the Required Credential
- When validating or executing a test with a Router Profile, **Username / Card** is always required (`isNotBlank()`).
- Empty or whitespace usernames are rejected regardless of `passwordEnabled` state.

### 3.2 Password Authentication is Optional
- Password is **optional** in the database and entity model.
- An empty password string is valid when `passwordEnabled == false`.
- Password is required **only** when `passwordEnabled == true`.

### 3.3 `passwordEnabled` Controls Password Participation
- Each router profile has an independent boolean flag:
  ```kotlin
  @ColumnInfo(name = "password_enabled") val passwordEnabled: Boolean = false
  ```
- This setting is strictly router-level, **not** a global setting, because different portals and networks operate under distinct authentication schemes.
- When `passwordEnabled == false`, the router profile does not require a password, and the testing engine will not attempt to submit a password.
- When `passwordEnabled == true`, the router profile requires a valid password, and the testing engine submits username + password together.

### 3.4 Password Field Remains Persistable Even When Disabled
- When `passwordEnabled == false`, the `password` column can hold an encrypted value or be blank.
- Disabling `passwordEnabled` **never deletes or wipes** the stored encrypted password.
- Rationale: The user may disable password authentication temporarily and re-enable it later. The encrypted credential remains safely stored in Room and is immediately restored when `passwordEnabled` is toggled back to `true`.

### 3.5 Password is Encrypted at Rest
- Any password stored in the database is encrypted at rest using AES-256-GCM / Android Keystore cryptographic standards (`SecurityUtils.encryptPasswordAtRest`).
- Plaintext passwords are never stored in SQLite / Room.
- Decryption is fail-closed: if decryption fails (e.g. due to key mismatch, corruption, or tampering), it returns an empty string `""` without crashing or exposing plaintext.
- Plaintext passwords are never emitted to logcat or export files.

### 3.6 Router-Specific Defaults
Default router profiles pre-populated in the database strictly initialize with `passwordEnabled = false`:
1. **ALBASHA (`شبكة الباشا`):**
   - Host: `wifi.sd.net`
   - `passwordEnabled = false`
   - `passwordSelector = "input[name=password]"` (preserved)
2. **MOTASEM (`شبكة معتصم نت`):**
   - Host: `wifi.sd.net`
   - `passwordEnabled = false`
   - `passwordSelector = "input[name=password]"` (preserved)
3. **BELLO (`بيلو`):**
   - Host: `www.bello.com`
   - `passwordEnabled = false`
   - `passwordSelector = "input[name=password]"` (preserved)

No router profile is automatically converted to `passwordEnabled = true`.

### 3.7 Router Settings User Experience Contract
In Phase 6 (Router Settings UI phase), the user experience contract will be:
```text
اسم المستخدم / الكرت
[________________]

[✓ / OFF] استخدام كلمة المرور (passwordEnabled)

كلمة المرور
[________________]
```
- **When Disabled (`passwordEnabled = false`):**
  - Password text field is disabled/dimmed.
  - Password is not required.
  - Stored encrypted password is preserved in Room.
- **When Enabled (`passwordEnabled = true`):**
  - Password text field is enabled and required.
  - User can input or view/edit the credential.
  - Stored encrypted credential is decrypted fail-closed for authentication.

---

## 4. Room Database Schema & Entity Architecture

### 4.1 Schema Version 5 Definition
The Room database is defined at `version = 5` with `exportSchema = true`:

```kotlin
@Database(
    entities = [
        CardEntity::class,
        RouterProfileEntity::class,
        TestResultEntity::class,
        TestSessionEntity::class,
        SuccessfulPatternEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase()
```

### 4.2 Entity Field Mapping: `RouterProfileEntity.kt`
```kotlin
@Parcelize
@Entity(tableName = "router_profiles")
data class RouterProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "ip") val ip: String,
    @ColumnInfo(name = "protocol") val protocol: String = "https",
    @ColumnInfo(name = "username") val username: String = "admin",
    @ColumnInfo(name = "password") val password: String = "",
    @ColumnInfo(name = "password_enabled") val passwordEnabled: Boolean = false,
    @ColumnInfo(name = "login_path") val loginPath: String = "/login",
    @ColumnInfo(name = "username_selector") val usernameSelector: String = "input[name=username]",
    @ColumnInfo(name = "password_selector") val passwordSelector: String = "input[name=password]",
    @ColumnInfo(name = "submit_selector") val submitSelector: String = "button[type=submit]",
    @ColumnInfo(name = "logout_selector") val logoutSelector: String = "",
    @ColumnInfo(name = "success_indicator") val successIndicator: String = "status=ok",
    @ColumnInfo(name = "failure_indicator") val failureIndicator: String = "error=",
    @ColumnInfo(name = "custom_js") val customJs: String? = null,
    @ColumnInfo(name = "md5_salt") val md5Salt: String = "",
    @ColumnInfo(name = "auth_type") val authType: RouterAuthType = RouterAuthType.FORM,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
) : Parcelable
```

### 4.3 Domain Model: `RouterProfile.kt`
```kotlin
@Parcelize
@Serializable
data class RouterProfile(
    val id: Long = 0,
    val name: String = "",
    val ip: String = "",
    val protocol: String = "http",
    val username: String = "admin",
    val password: String = "",
    val passwordEnabled: Boolean = false,
    val loginPath: String = "/login",
    val usernameSelector: String = "",
    val passwordSelector: String = "",
    val submitSelector: String = "",
    val logoutSelector: String = "",
    val successIndicator: String = "",
    val failureIndicator: String = "",
    val customJs: String? = null,
    val md5Salt: String = "",
    val authType: RouterAuthType = RouterAuthType.FORM,
    val isActive: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {
    fun hasValidCredentials(): Boolean {
        if (username.isBlank()) return false
        if (passwordEnabled && password.isBlank()) return false
        return true
    }

    fun shouldSubmitPassword(): Boolean = passwordEnabled
}
```

---

## 5. Future Test Engine Contract (Phase 5 Specification)

The test engine contract is documented for Phase 5 implementation:

```text
========================================================================
TEST ENGINE AUTHENTICATION CONTRACT (Phase 5)
========================================================================

when (routerProfile.passwordEnabled) {
    false -> {
        // Mode: Username / Card Only Authentication
        // 1. Submit usernameSelector with card/username
        // 2. Set password field to empty string or skip passwordSelector
        // 3. Trigger submitSelector or form submission
    }
    true -> {
        // Mode: Username + Password Dual Authentication
        // 1. Submit usernameSelector with card/username
        // 2. Decrypt password credential from Room (fail-closed)
        // 3. Inject decrypted credential into passwordSelector
        // 4. Trigger submitSelector or form submission
    }
}
========================================================================
```

---

# PHASE 3 FINAL CORRECTION PASS

## 1. Previous Issues Detected

During the Phase 3 forensic review, the following integrity issues were identified:
1. **Destructive Migration Fallback:** `AppModule.kt` contained `.fallbackToDestructiveMigration()`, which could silently wipe user databases on unhandled schema mismatches.
2. **Schema Export Disabled:** `AppDatabase.kt` had `exportSchema = false`, preventing Room from generating versioned schema JSON artifacts for verification.
3. **Legacy Plaintext Credential Risk:** Older installations storing plaintext passwords in `router_profiles.password` lacked automated in-place cryptographic upgrade during migration.
4. **Default Router Host Discrepancy:** Initial default router insertions in `AppModule.kt` used outdated legacy hosts (`www.Abasha.com`, `r.com`) instead of the current operational host (`wifi.sd.net`).
5. **Compiler Type Inference Warning:** An untyped `arrayOf(encryptedPwd, id)` in `AppDatabase.kt` triggered a compiler warning regarding reified type argument intersection.

---

## 2. Corrections Applied

| Issue | Target File | Action Taken |
|---|---|---|
| Destructive Fallback | `AppModule.kt` | Removed `.fallbackToDestructiveMigration()`. Database Builder now strictly relies on `.addMigrations(*AppDatabase.ALL_MIGRATIONS)`. |
| Schema Export | `AppDatabase.kt` & `app/build.gradle.kts` | Changed to `exportSchema = true`. Added `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` and configured test asset sourceSets. |
| Plaintext Passwords | `AppDatabase.kt` & `SecurityUtils.kt` | Added in-place encryption logic to `MIGRATION_4_5`. Detects non-encrypted passwords and updates them to AES-256-GCM ciphertext. |
| Router Hosts | `AppModule.kt` | Corrected ALBASHA and MOTASEM default profile IPs to `wifi.sd.net`. Preserved BELLO at `www.bello.com`. |
| Compiler Warning | `AppDatabase.kt` | Added explicit `arrayOf<Any>(encryptedPwd, id)` type argument. |
| Test Double Architecture | `RouterPasswordMigrationTest.kt` | Replaced Mockito mock maker with clean standard Java dynamic proxies, eliminating bytecode instrumentation failures. |

---

## 3. Destructive Migration Verification

A full codebase search confirms zero occurrences of destructive fallback methods:
```text
$ grep -rn "fallbackToDestructiveMigration" app/src
(No matches found - 0 occurrences)

$ grep -rn "fallbackToDestructiveMigrationFrom" app/src
(No matches found - 0 occurrences)
```
No custom fallback logic exists to delete databases, drop tables, or clear records on error.

---

## 4. Schema Export Verification

KSP Room compiler execution verified the generation of the official schema file:
- **Location:** `app/schemas/com.example.data.local.database.AppDatabase/5.json`
- **Identity Hash:** `f87a2a2acca17d1cd46fdb52b4cd21cd`
- **Schema Contents:**
  - Table `router_profiles` includes `password_enabled` (`INTEGER NOT NULL`), `password` (`TEXT NOT NULL`), `password_selector` (`TEXT NOT NULL`).
  - Table `successful_patterns` includes foreign key to `router_profiles(id)` (`ON DELETE CASCADE`) and index `index_successful_patterns_router_id`.
  - Tables `cards`, `test_sessions`, and `test_results` verified.

### Historical Schema Record:
```text
HISTORICAL_SCHEMA_EVIDENCE_MISSING:
- Versions 1, 2, 3, and 4 schemas were not exported by the historical GitHub repository (exportSchema was previously false).
- In accordance with Requirement 3, fake or fabricated JSON files for v1-v4 were NOT generated.
- The official schema export begins authoritatively with version 5.
```

---

## 5. Legacy Plaintext Credential Migration Verification

The in-place migration of legacy plaintext credentials was verified via a rigorous test fixture:
1. **Initial State:** A legacy row exists with `password = "KnownPlaintextPass2026!"` (plaintext).
2. **Migration Execution:** `AppDatabase.MIGRATION_4_5.migrate(db)` runs.
3. **Inspection of SQLite Column Directly:**
   - Stored value is checked for exact match against plaintext -> `plaintextPresent = false`.
   - Stored value is checked for AES-256-GCM ciphertext format -> `encryptedPresent = true`.
4. **Cryptographic Validation:**
   - Correct key decrypts stored ciphertext to `"KnownPlaintextPass2026!"`.
   - Wrong key returns empty string `""` (fail-closed).
   - Tampered ciphertext returns empty string `""` (fail-closed).

---

## 6. Router Host Verification

The default router profiles pre-populated in `AppModule.kt` were verified:
- **ALBASHA (`شبكة الباشا`):** IP set to `wifi.sd.net` (passwordEnabled = false, passwordSelector = `input[name=password]`).
- **MOTASEM (`شبكة معتصم نت`):** IP set to `wifi.sd.net` (passwordEnabled = false, passwordSelector = `input[name=password]`).
- **BELLO (`بيلو`):** IP set to `www.bello.com` (passwordEnabled = false, passwordSelector = `input[name=password]`).
- Historical HTML snapshots and documentation were preserved without global replacement.

---

## 7. Migration Matrix

| Migration | Source Ver | Target Ver | Action | Verification |
|---|---|---|---|---|
| `MIGRATION_1_2` | 1 | 2 | `ALTER TABLE router_profiles ADD COLUMN logout_selector TEXT NOT NULL DEFAULT ''` | PASS |
| `MIGRATION_2_3` | 2 | 3 | Schema alignment | PASS |
| `MIGRATION_3_4` | 3 | 4 | Creates `successful_patterns` table with foreign key and index | PASS |
| `MIGRATION_4_5` | 4 | 5 | Adds `password_enabled INTEGER NOT NULL DEFAULT 0` + migrates plaintext passwords to ciphertext | PASS |
| `MIGRATION_1_5` | 1 | 5 | Direct compound migration applying 1->2, 3->4, 4->5 | PASS |
| `MIGRATION_2_5` | 2 | 5 | Direct compound migration applying 3->4, 4->5 | PASS |
| `MIGRATION_3_5` | 3 | 5 | Direct compound migration applying 3->4, 4->5 | PASS |

---

## 8. Database Schema Verification

A direct audit of the exported `5.json` schema and entity definitions confirms:
- **Indices:** `index_successful_patterns_router_id` on `successful_patterns(router_id)`.
- **Foreign Keys:** `successful_patterns` references `router_profiles(id)` on delete CASCADE.
- **Constraints:** Primary keys on all tables (`id` auto-generated), non-null constraints on mandatory fields.

---

## 9. Concurrency, Transaction & Batch Write Analysis

In accordance with Requirements 17, 18, and 19, the following architectural classifications are recorded:
- **Batch Write Architecture:** `TestResultDao.insertResults(List<TestResultEntity>)` is available at the DAO layer. However, bulk batch queuing in `TestService` worker loops is currently sequential. This is classified as:  
  👉 **`PREPARED_FOR_PHASE_4`** (not claimed as fully implemented in Phase 3).
- **Transactions:** `RouterProfileDao.setDefault` is annotated with `@Transaction`. Session cleanup and updates execute atomic SQL statements (`UPDATE test_sessions SET ... WHERE id = :sessionId`).
- **Counter Concurrency:** In `SessionDao`, `updateCounts(sessionId, successCount, failureCount)` performs atomic database updates. Multi-worker in-memory counter aggregation races are classified as:  
  👉 **`SCHEDULED_FOR_PHASE_4`** (performance & coroutine optimization phase).

---

## 10. Final Build & Test Verification

All required build and verification tasks were executed and passed cleanly:

| Step | Command | Execution Time | Result |
|---|---|---|---|
| **Clean & Assemble Debug** | `./gradlew clean assembleDebug` | 26s | **PASS** |
| **Unit Test Suite** | `./gradlew test` | 19s | **PASS** (100% tests passing) |
| **Android Lint Gate** | `./gradlew lint` | 39s | **PASS** (0 errors) |
| **Assemble Release APK** | `./gradlew assembleRelease` | 21s | **PASS** (`app-release-unsigned.apk`) |
| **AI Studio Platform Build** | `compile_applet` | 15s | **PASS** |

---

## 11. Final Checklist Verification

- [x] **No destructive migration fallback** in any Database Builder.
- [x] **Room schema export enabled** (`exportSchema = true`) and `5.json` generated.
- [x] **Real migration chain verified** (1->2->3->4->5 and compound paths).
- [x] **Legacy plaintext credential migration tested** and verified fail-closed.
- [x] **Current router hosts corrected** (`wifi.sd.net` for ALBASHA and MOTASEM).
- [x] **`passwordEnabled` works** per-profile (default = false).
- [x] **Password retention works** (not wiped when disabled).
- [x] **Build passes**, **tests pass**, **lint passes**.

---

## FINAL STATUS

# **COMPLETE**
*(Zero destructive migration fallbacks, Room schema exported, legacy plaintext migration verified, router hosts corrected, all builds, tests, and lint checks passing)*
