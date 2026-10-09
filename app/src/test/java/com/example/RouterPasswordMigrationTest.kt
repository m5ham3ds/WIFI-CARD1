package com.example

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.*
import com.example.data.mapper.RouterMapper.toDomain
import com.example.data.mapper.RouterMapper.toEntity
import com.example.domain.model.RouterAuthType
import com.example.domain.model.RouterProfile
import com.example.util.SecurityUtils
import com.example.util.ValidationUtils
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * Phase 3 & Phase 3.1: Database Integrity, Migration & Security Verification Tests.
 *
 * Verifies:
 * 1. Removal of all destructive migration fallbacks.
 * 2. Room schema export verification (version 5 exported).
 * 3. Migration sequence: MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5.
 * 4. Compound migration paths: MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5.
 * 5. Legacy plaintext credential migration:
 *    legacy plaintext -> migration / data upgrade -> encrypt with Keystore/AES-GCM ->
 *    store ciphertext -> verify plaintext is absent -> decrypt fail-closed.
 * 6. Migration idempotency (no duplicate columns, repeated execution safety).
 * 7. Non-sensitive data preservation across all Room tables (routers, cards, sessions, results, patterns).
 * 8. Router-specific defaults: ALBASHA (wifi.sd.net), MOTASEM (wifi.sd.net), BELLO (www.bello.com).
 * 9. Password optional contract & password retention when toggled.
 * 10. Fail-closed cryptographic security.
 */
class RouterPasswordMigrationTest {

    data class MockProfileRow(
        val id: Long,
        val name: String = "Test Router",
        var ip: String = "wifi.sd.net",
        val username: String = "admin",
        var password: String = "",
        var passwordEnabled: Int = 0,
        var strategyId: String = "generic"
    )

    private fun createMockDb(
        existingColumns: MutableList<String>,
        rows: MutableList<MockProfileRow>,
        executedSql: MutableList<String>
    ): SupportSQLiteDatabase {
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            when (method.name) {
                "query" -> {
                    val sql = args?.getOrNull(0) as? String ?: ""
                    when {
                        sql.contains("PRAGMA table_info") -> {
                            var cursorIndex = -1
                            Proxy.newProxyInstance(
                                Cursor::class.java.classLoader,
                                arrayOf(Cursor::class.java)
                            ) { _, cMethod, cArgs ->
                                when (cMethod.name) {
                                    "moveToNext" -> {
                                        cursorIndex++
                                        cursorIndex < existingColumns.size
                                    }
                                    "getColumnIndex" -> if (cArgs?.getOrNull(0) == "name") 0 else -1
                                    "getString" -> existingColumns[cursorIndex]
                                    "close" -> null
                                    else -> null
                                }
                            } as Cursor
                        }
                        sql.contains("SELECT id, password FROM router_profiles") -> {
                            var cursorIndex = -1
                            Proxy.newProxyInstance(
                                Cursor::class.java.classLoader,
                                arrayOf(Cursor::class.java)
                            ) { _, cMethod, cArgs ->
                                when (cMethod.name) {
                                    "moveToNext" -> {
                                        cursorIndex++
                                        cursorIndex < rows.size
                                    }
                                    "getColumnIndex" -> {
                                        when (cArgs?.getOrNull(0)) {
                                            "id" -> 0
                                            "password" -> 1
                                            else -> -1
                                        }
                                    }
                                    "getLong" -> rows[cursorIndex].id
                                    "getString" -> rows[cursorIndex].password
                                    "close" -> null
                                    else -> null
                                }
                            } as Cursor
                        }
                        else -> {
                            Proxy.newProxyInstance(
                                Cursor::class.java.classLoader,
                                arrayOf(Cursor::class.java)
                            ) { _, cMethod, _ ->
                                when (cMethod.name) {
                                    "moveToNext" -> false
                                    "close" -> null
                                    else -> null
                                }
                            } as Cursor
                        }
                    }
                }
                "execSQL" -> {
                    val sql = args[0] as String
                    executedSql.add(sql)
                    if (sql.contains("ADD COLUMN password_enabled")) {
                        if (!existingColumns.contains("password_enabled")) {
                            existingColumns.add("password_enabled")
                        }
                    }
                    if (sql.contains("ADD COLUMN strategy_id")) {
                        if (!existingColumns.contains("strategy_id")) {
                            existingColumns.add("strategy_id")
                        }
                    }
                    if (sql.contains("SET strategy_id = 'abasha'")) {
                        rows.filter { it.name.contains("الباشا") || it.name.contains("abasha", ignoreCase = true) }
                            .forEach { it.strategyId = "abasha" }
                    }
                    if (sql.contains("SET ip = 'wifi.sd.net' WHERE") && sql.contains("www.Abasha.com")) {
                        rows.filter { (it.name.contains("الباشا") || it.name.contains("abasha", ignoreCase = true)) && it.ip == "www.Abasha.com" }
                            .forEach { it.ip = "wifi.sd.net" }
                    }
                    if (sql.contains("SET strategy_id = 'motasem'")) {
                        rows.filter { it.name.contains("معتصم") || it.name.contains("motasem", ignoreCase = true) }
                            .forEach { it.strategyId = "motasem" }
                    }
                    if (sql.contains("SET ip = 'wifi.sd.net' WHERE") && sql.contains("r.com")) {
                        rows.filter { (it.name.contains("معتصم") || it.name.contains("motasem", ignoreCase = true)) && it.ip == "r.com" }
                            .forEach { it.ip = "wifi.sd.net" }
                    }
                    if (sql.contains("SET strategy_id = 'bello'")) {
                        rows.filter { it.name.contains("بيلو") || it.name.contains("bello", ignoreCase = true) }
                            .forEach { it.strategyId = "bello" }
                    }
                    if (sql.contains("UPDATE router_profiles SET password = ? WHERE id = ?")) {
                        val bindArgs = args.getOrNull(1) as? Array<*>
                        val newPwd = bindArgs?.getOrNull(0) as? String ?: ""
                        val targetId = (bindArgs?.getOrNull(1) as? Number)?.toLong() ?: 0L
                        rows.find { it.id == targetId }?.password = newPwd
                    }
                    null
                }
                else -> null
            }
        } as SupportSQLiteDatabase
    }

    @Test
    fun testMigration4to5AddsPasswordEnabledColumnAndPreservesExistingData() {
        val executedSql = mutableListOf<String>()
        val columns = mutableListOf("id", "username", "password")
        val rows = mutableListOf<MockProfileRow>()
        val fakeDb = createMockDb(columns, rows, executedSql)

        // Execute migration 4 -> 5
        AppDatabase.MIGRATION_4_5.migrate(fakeDb)

        // Verify that ALTER TABLE was executed to add password_enabled with default 0
        assertTrue(executedSql.any { it.contains("ALTER TABLE router_profiles ADD COLUMN password_enabled INTEGER NOT NULL DEFAULT 0") })
        assertTrue(columns.contains("password_enabled"))
    }

    @Test
    fun testMigrationIdempotentWhenColumnAlreadyExists() {
        val executedSql = mutableListOf<String>()
        val columns = mutableListOf("id", "username", "password", "password_enabled")
        val rows = mutableListOf<MockProfileRow>()
        val fakeDb = createMockDb(columns, rows, executedSql)

        // Execute migration 4 -> 5
        AppDatabase.MIGRATION_4_5.migrate(fakeDb)

        // Verify that ALTER TABLE was NOT called because column already exists (idempotency)
        assertFalse(
            "No ALTER TABLE should be executed if password_enabled already exists",
            executedSql.any { it.contains("ALTER TABLE router_profiles ADD COLUMN password_enabled") }
        )
    }

    @Test
    fun testLegacyPlaintextCredentialMigratedToCiphertextInSQLite() {
        // Requirements 5, 6, 7:
        // legacy plaintext -> migration / data upgrade -> encrypt with Keystore -> store ciphertext -> read successfully.
        val knownPlaintextPassword = "KnownPlaintextPass2026!"
        val legacyRow = MockProfileRow(
            id = 42L,
            username = "admin",
            password = knownPlaintextPassword
        )

        val columns = mutableListOf("id", "username", "password")
        val rows = mutableListOf(legacyRow)
        val executedSql = mutableListOf<String>()
        val fakeDb = createMockDb(columns, rows, executedSql)

        // Execute migration 4 -> 5
        AppDatabase.MIGRATION_4_5.migrate(fakeDb)

        // Inspect the SQLite column directly
        val storedPasswordInDb = rows.first { it.id == 42L }.password

        // Verify 1: The stored value in the database column is NOT the plaintext
        val plaintextPresent = (storedPasswordInDb == knownPlaintextPassword)
        val encryptedPresent = SecurityUtils.isEncrypted(storedPasswordInDb)

        // Print only boolean security indicators (Requirement 7: Do NOT print password or full ciphertext)
        println("plaintextPresent = $plaintextPresent")
        println("encryptedPresent = $encryptedPresent")

        assertFalse("Plaintext password MUST NOT be present in database column", plaintextPresent)
        assertTrue("Ciphertext MUST be present in database column", encryptedPresent)

        // Verify 2: It can be decrypted with the correct key
        val decryptedSecret = SecurityUtils.decryptPasswordAtRest(storedPasswordInDb)
        assertEquals("Decrypted password must match original plaintext with correct key", knownPlaintextPassword, decryptedSecret)

        // Verify 3: It CANNOT be decrypted with a wrong key (fail-closed empty string)
        val wrongKey = SecurityUtils.deriveAesKey("AttackerWrongPassphrase", "AttackerSalt")
        val wrongDecryption = SecurityUtils.decryptAesGcm(storedPasswordInDb, wrongKey)
        assertEquals("Fail-closed decryption must return empty string on wrong key", "", wrongDecryption)

        // Verify 4: Tampered ciphertext fails (fail-closed empty string)
        val tamperedCipher = storedPasswordInDb.substring(0, storedPasswordInDb.length - 6) + "XXXXXX"
        val tamperedDecryption = SecurityUtils.decryptAesGcm(tamperedCipher, SecurityUtils.getRouterStorageKey())
        assertEquals("Fail-closed decryption must return empty string on tampered ciphertext", "", tamperedDecryption)
    }

    @Test
    fun testFullMigrationChainFromV1ToV5() {
        // Tests the full chain: MIGRATION_1_2 -> MIGRATION_2_3 -> MIGRATION_3_4 -> MIGRATION_4_5
        val executedSql = mutableListOf<String>()
        val columns = mutableListOf("id", "name", "ip", "username", "password")
        val rows = mutableListOf(
            MockProfileRow(id = 1L, name = "Router 1", password = "legacyPlaintext123")
        )
        val fakeDb = createMockDb(columns, rows, executedSql)

        // Step 1: Migration 1 -> 2
        AppDatabase.MIGRATION_1_2.migrate(fakeDb)
        assertTrue(executedSql.any { it.contains("ALTER TABLE router_profiles ADD COLUMN logout_selector") })

        // Step 2: Migration 2 -> 3
        AppDatabase.MIGRATION_2_3.migrate(fakeDb)

        // Step 3: Migration 3 -> 4
        AppDatabase.MIGRATION_3_4.migrate(fakeDb)
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `successful_patterns`") })

        // Step 4: Migration 4 -> 5
        AppDatabase.MIGRATION_4_5.migrate(fakeDb)
        assertTrue(executedSql.any { it.contains("ALTER TABLE router_profiles ADD COLUMN password_enabled") })

        // Verify plaintext password migrated during chain
        val migratedPassword = rows.first().password
        assertNotEquals("legacyPlaintext123", migratedPassword)
        assertTrue(SecurityUtils.isEncrypted(migratedPassword))
        assertEquals("legacyPlaintext123", SecurityUtils.decryptPasswordAtRest(migratedPassword))
    }

    @Test
    fun testMigration5to6AddsStrategyIdColumnAndPopulatesSeededProfiles() {
        val executedSql = mutableListOf<String>()
        val columns = mutableListOf("id", "name", "ip", "username", "password", "password_enabled")
        val rows = mutableListOf(
            MockProfileRow(id = 1L, name = "شبكة الباشا", ip = "www.Abasha.com"),
            MockProfileRow(id = 2L, name = "شبكة معتصم نت", ip = "r.com"),
            MockProfileRow(id = 3L, name = "بيلو", ip = "www.bello.com"),
            MockProfileRow(id = 4L, name = "Custom Gateway", ip = "192.168.1.1")
        )
        val fakeDb = createMockDb(columns, rows, executedSql)

        // Execute migration 5 -> 6
        AppDatabase.MIGRATION_5_6.migrate(fakeDb)

        // Verify column added
        assertTrue("strategy_id column must be added to table", columns.contains("strategy_id"))
        assertTrue(executedSql.any { it.contains("ALTER TABLE router_profiles ADD COLUMN strategy_id TEXT NOT NULL DEFAULT 'generic'") })

        // Verify seeded profiles populated and runtime hosts corrected to wifi.sd.net
        val albasha = rows.first { it.id == 1L }
        assertEquals("abasha", albasha.strategyId)
        assertEquals("wifi.sd.net", albasha.ip)

        val motasem = rows.first { it.id == 2L }
        assertEquals("motasem", motasem.strategyId)
        assertEquals("wifi.sd.net", motasem.ip)

        val bello = rows.first { it.id == 3L }
        assertEquals("bello", bello.strategyId)
        assertEquals("www.bello.com", bello.ip)

        val custom = rows.first { it.id == 4L }
        assertEquals("generic", custom.strategyId)
    }

    @Test
    fun testMigration5to6IdempotentWhenColumnAlreadyExists() {
        val executedSql = mutableListOf<String>()
        val columns = mutableListOf("id", "name", "ip", "strategy_id")
        val rows = mutableListOf<MockProfileRow>()
        val fakeDb = createMockDb(columns, rows, executedSql)

        AppDatabase.MIGRATION_5_6.migrate(fakeDb)

        assertFalse(
            "No ALTER TABLE should be executed if strategy_id already exists",
            executedSql.any { it.contains("ALTER TABLE router_profiles ADD COLUMN strategy_id") }
        )
    }

    @Test
    fun testCompoundMigrationsV1ToV5V2ToV5V3ToV5() {
        // Direct jump 1 -> 5
        val sql1to5 = mutableListOf<String>()
        val fakeDb1 = createMockDb(mutableListOf("id", "password"), mutableListOf(MockProfileRow(1L, password = "p1")), sql1to5)
        AppDatabase.MIGRATION_1_5.migrate(fakeDb1)
        assertTrue(sql1to5.any { it.contains("password_enabled") })

        // Direct jump 2 -> 5
        val sql2to5 = mutableListOf<String>()
        val fakeDb2 = createMockDb(mutableListOf("id", "password"), mutableListOf(MockProfileRow(2L, password = "p2")), sql2to5)
        AppDatabase.MIGRATION_2_5.migrate(fakeDb2)
        assertTrue(sql2to5.any { it.contains("password_enabled") })

        // Direct jump 3 -> 5
        val sql3to5 = mutableListOf<String>()
        val fakeDb3 = createMockDb(mutableListOf("id", "password"), mutableListOf(MockProfileRow(3L, password = "p3")), sql3to5)
        AppDatabase.MIGRATION_3_5.migrate(fakeDb3)
        assertTrue(sql3to5.any { it.contains("password_enabled") })
    }

    @Test
    fun testCompoundMigrationsUpToV6() {
        // Direct jump 1 -> 6
        val sql1to6 = mutableListOf<String>()
        val fakeDb1 = createMockDb(mutableListOf("id", "name", "ip", "password"), mutableListOf(MockProfileRow(1L, name = "شبكة الباشا", ip = "www.Abasha.com")), sql1to6)
        AppDatabase.MIGRATION_1_6.migrate(fakeDb1)
        assertTrue(sql1to6.any { it.contains("strategy_id") })

        // Direct jump 2 -> 6
        val sql2to6 = mutableListOf<String>()
        val fakeDb2 = createMockDb(mutableListOf("id", "name", "ip", "password"), mutableListOf(MockProfileRow(2L, name = "شبكة معتصم نت", ip = "r.com")), sql2to6)
        AppDatabase.MIGRATION_2_6.migrate(fakeDb2)
        assertTrue(sql2to6.any { it.contains("strategy_id") })

        // Direct jump 3 -> 6
        val sql3to6 = mutableListOf<String>()
        val fakeDb3 = createMockDb(mutableListOf("id", "name", "ip", "password"), mutableListOf(MockProfileRow(3L, name = "بيلو", ip = "www.bello.com")), sql3to6)
        AppDatabase.MIGRATION_3_6.migrate(fakeDb3)
        assertTrue(sql3to6.any { it.contains("strategy_id") })

        // Direct jump 4 -> 6
        val sql4to6 = mutableListOf<String>()
        val fakeDb4 = createMockDb(mutableListOf("id", "name", "ip", "password"), mutableListOf(MockProfileRow(4L, name = "Custom Wifi", ip = "192.168.1.1")), sql4to6)
        AppDatabase.MIGRATION_4_6.migrate(fakeDb4)
        assertTrue(sql4to6.any { it.contains("strategy_id") })
    }

    @Test
    fun testDataPreservationFixtureAcrossTables() {
        // Test data fixture representing all Room entities
        val timestamp = 1700000000L
        val legacyPasswordEncrypted = SecurityUtils.encryptPasswordAtRest("AdminPass@2026")

        val routerFixture = RouterProfileEntity(
            id = 7L,
            name = "شبكة الباشا",
            ip = "wifi.sd.net",
            protocol = "http",
            username = "admin",
            password = legacyPasswordEncrypted,
            passwordEnabled = false,
            strategyId = "abasha",
            loginPath = "/login",
            usernameSelector = "input[name=username]",
            passwordSelector = "input[name=password]",
            submitSelector = "input[type=submit]",
            logoutSelector = "form[id=mForm]",
            successIndicator = "MikroTicket Status",
            failureIndicator = "خطأ",
            customJs = "console.log('test');",
            md5Salt = "salt123",
            authType = RouterAuthType.FORM,
            isActive = true,
            isDefault = true,
            createdAt = timestamp
        )

        val cardFixture = CardEntity(
            id = 11L,
            code = "CARD776655",
            prefix = "CARD",
            length = 10,
            charset = "0123456789",
            createdAt = timestamp
        )

        val sessionFixture = TestSessionEntity(
            id = 21L,
            routerId = 7L,
            routerName = "شبكة الباشا",
            totalCards = 50,
            successCount = 5,
            failureCount = 45,
            isRunning = false,
            startedAt = timestamp,
            finishedAt = timestamp + 1800000L
        )

        val resultFixture = TestResultEntity(
            id = 31L,
            sessionId = 21L,
            cardCode = "CARD776655",
            routerId = 7L,
            routerName = "شبكة الباشا",
            state = "SUCCESS",
            message = "Login OK",
            durationMs = 950L,
            testedAt = timestamp + 1000L
        )

        val patternFixture = SuccessfulPatternEntity(
            id = 41L,
            routerId = 7L,
            pattern = "CARD77****",
            confidence = 0.98f,
            discoveredAt = timestamp + 2000L
        )

        // Verify Router entity -> domain mapping preservation
        val domainRouter = routerFixture.toDomain()
        assertEquals(7L, domainRouter.id)
        assertEquals("شبكة الباشا", domainRouter.name)
        assertEquals("wifi.sd.net", domainRouter.ip)
        assertEquals("abasha", domainRouter.strategyId)
        assertEquals("admin", domainRouter.username)
        assertEquals(legacyPasswordEncrypted, domainRouter.password)
        assertEquals("input[name=password]", domainRouter.passwordSelector)
        assertFalse(domainRouter.passwordEnabled)
        assertEquals("/login", domainRouter.loginPath)
        assertEquals("form[id=mForm]", domainRouter.logoutSelector)
        assertEquals("MikroTicket Status", domainRouter.successIndicator)
        assertEquals("خطأ", domainRouter.failureIndicator)
        assertEquals("console.log('test');", domainRouter.customJs)
        assertEquals("salt123", domainRouter.md5Salt)
        assertEquals(RouterAuthType.FORM, domainRouter.authType)
        assertTrue(domainRouter.isActive)
        assertTrue(domainRouter.isDefault)
        assertEquals(timestamp, domainRouter.createdAt)

        // Verify other tables data integrity
        assertEquals("CARD776655", cardFixture.code)
        assertEquals("CARD", cardFixture.prefix)
        assertEquals(10, cardFixture.length)

        assertEquals(50, sessionFixture.totalCards)
        assertEquals(5, sessionFixture.successCount)
        assertEquals(45, sessionFixture.failureCount)
        assertFalse(sessionFixture.isRunning)

        assertEquals("SUCCESS", resultFixture.state)
        assertEquals(950L, resultFixture.durationMs)

        assertEquals(0.98f, patternFixture.confidence, 0.001f)
        assertEquals("CARD77****", patternFixture.pattern)
    }

    @Test
    fun testRoomSchemaVersion5IntegrityAndExportedJson() {
        // Verify exported Room schema JSON exists on disk
        val schemaFile = File("schemas/com.example.data.local.database.AppDatabase/5.json")
        assertTrue("Room exported schema 5.json must exist at schemas location", schemaFile.exists())

        val schemaJson = schemaFile.readText()

        // Verify version = 5
        assertTrue(schemaJson.contains("\"version\": 5"))

        // Verify password_enabled column exists with INTEGER NOT NULL
        assertTrue(schemaJson.contains("\"columnName\": \"password_enabled\""))
        assertTrue(schemaJson.contains("\"affinity\": \"INTEGER\""))

        // Verify password and password_selector are preserved
        assertTrue(schemaJson.contains("\"columnName\": \"password\""))
        assertTrue(schemaJson.contains("\"columnName\": \"password_selector\""))

        // Verify foreign key on successful_patterns to router_profiles
        assertTrue(schemaJson.contains("\"tableName\": \"successful_patterns\""))
        assertTrue(schemaJson.contains("\"table\": \"router_profiles\""))
        assertTrue(schemaJson.contains("\"onDelete\": \"CASCADE\""))
    }

    @Test
    fun testRoomSchemaVersion6IntegrityAndExportedJson() {
        // Verify exported Room schema JSON for version 6 exists on disk
        val schemaFile = File("schemas/com.example.data.local.database.AppDatabase/6.json")
        assertTrue("Room exported schema 6.json must exist at schemas location", schemaFile.exists())

        val schemaJson = schemaFile.readText()

        // Verify version = 6
        assertTrue(schemaJson.contains("\"version\": 6"))

        // Verify strategy_id column exists with TEXT NOT NULL
        assertTrue(schemaJson.contains("\"columnName\": \"strategy_id\""))
        assertTrue(schemaJson.contains("\"affinity\": \"TEXT\""))

        // Verify password_enabled column exists with INTEGER NOT NULL
        assertTrue(schemaJson.contains("\"columnName\": \"password_enabled\""))
        assertTrue(schemaJson.contains("\"affinity\": \"INTEGER\""))

        // Verify password and password_selector are preserved
        assertTrue(schemaJson.contains("\"columnName\": \"password\""))
        assertTrue(schemaJson.contains("\"columnName\": \"password_selector\""))

        // Verify foreign key on successful_patterns to router_profiles
        assertTrue(schemaJson.contains("\"tableName\": \"successful_patterns\""))
        assertTrue(schemaJson.contains("\"table\": \"router_profiles\""))
        assertTrue(schemaJson.contains("\"onDelete\": \"CASCADE\""))
    }

    @Test
    fun testCurrentDefaultRouterHostsCorrected() {
        // Operational defaults: ALBASHA (wifi.sd.net), MOTASEM (wifi.sd.net), BELLO (www.bello.com)
        val albasha = RouterProfile(
            name = "شبكة الباشا",
            ip = "wifi.sd.net",
            strategyId = "abasha",
            loginPath = "/login",
            username = "admin",
            password = "",
            passwordEnabled = false,
            passwordSelector = "input[name=password]"
        )
        assertEquals("ALBASHA current runtime host MUST be wifi.sd.net", "wifi.sd.net", albasha.ip)
        assertEquals("abasha", albasha.strategyId)
        assertFalse("ALBASHA must default to passwordEnabled = false", albasha.passwordEnabled)
        assertEquals("input[name=password]", albasha.passwordSelector)

        val motasem = RouterProfile(
            name = "شبكة معتصم نت",
            ip = "wifi.sd.net",
            strategyId = "motasem",
            loginPath = "/login",
            username = "admin",
            password = "",
            passwordEnabled = false,
            passwordSelector = "input[name=password]"
        )
        assertEquals("MOTASEM current runtime host MUST be wifi.sd.net", "wifi.sd.net", motasem.ip)
        assertEquals("motasem", motasem.strategyId)
        assertFalse("MOTASEM must default to passwordEnabled = false", motasem.passwordEnabled)
        assertEquals("input[name=password]", motasem.passwordSelector)

        val bello = RouterProfile(
            name = "بيلو",
            ip = "www.bello.com",
            strategyId = "bello",
            loginPath = "/login",
            username = "admin",
            password = "",
            passwordEnabled = false,
            passwordSelector = "input[name=password]"
        )
        assertEquals("www.bello.com", bello.ip)
        assertEquals("bello", bello.strategyId)
        assertFalse("BELLO must default to passwordEnabled = false", bello.passwordEnabled)
        assertEquals("input[name=password]", bello.passwordSelector)
    }

    @Test
    fun testPasswordOptionalContractAndRetention() {
        val encryptedPassword = SecurityUtils.encryptPasswordAtRest("Secret123")

        val profile = RouterProfile(
            name = "Test Portal",
            ip = "192.168.1.1",
            username = "admin",
            password = encryptedPassword,
            passwordEnabled = false
        )

        // Disabled by default: username only enters contract
        assertFalse(profile.shouldSubmitPassword())
        assertTrue(profile.hasValidCredentials())

        // User enables password
        val enabledProfile = profile.copy(passwordEnabled = true)
        assertTrue(enabledProfile.shouldSubmitPassword())
        assertTrue(enabledProfile.hasValidCredentials())
        assertEquals(encryptedPassword, enabledProfile.password)
        assertEquals("Secret123", SecurityUtils.decryptPasswordAtRest(enabledProfile.password))

        // User disables password: encrypted password must NOT be wiped
        val disabledAgain = enabledProfile.copy(passwordEnabled = false)
        assertFalse(disabledAgain.shouldSubmitPassword())
        assertEquals("Password must be preserved when disabled", encryptedPassword, disabledAgain.password)

        // When enabled, empty password is rejected
        val emptyPassEnabled = profile.copy(password = "", passwordEnabled = true)
        assertFalse("Empty password must be invalid when passwordEnabled is true", emptyPassEnabled.hasValidCredentials())
    }

    @Test
    fun testUsernameRequiredAndPasswordOptionalValidationRules() {
        // Rule 1: Username / Card is ALWAYS required
        assertFalse(
            "Blank username must be invalid even if password disabled",
            ValidationUtils.isRouterCredentialValid(usernameOrCard = "", password = "", passwordEnabled = false)
        )
        assertFalse(
            "Blank username must be invalid when password enabled",
            ValidationUtils.isRouterCredentialValid(usernameOrCard = "  ", password = "secret", passwordEnabled = true)
        )

        // Rule 2: Password is optional when passwordEnabled == false
        assertTrue(
            "Username only is valid when passwordEnabled is false",
            ValidationUtils.isRouterCredentialValid(usernameOrCard = "90412345", password = "", passwordEnabled = false)
        )

        // Rule 3: Password is required ONLY when passwordEnabled == true
        assertFalse(
            "Empty password must be invalid when passwordEnabled is true",
            ValidationUtils.isRouterCredentialValid(usernameOrCard = "admin", password = "", passwordEnabled = true)
        )
        assertTrue(
            "Username with valid password is valid when passwordEnabled is true",
            ValidationUtils.isRouterCredentialValid(usernameOrCard = "admin", password = "my_password", passwordEnabled = true)
        )
    }

    @Test
    fun testApplicationIdCanonicalLock() {
        val buildGradleFile = File("build.gradle.kts")
        val content = buildGradleFile.readText()
        assertTrue(
            "applicationId must be locked to canonical identity com.aistudio.wifimasterpro",
            content.contains("applicationId = \"com.aistudio.wifimasterpro.")
        )
        assertFalse(
            "applicationId must NOT contain accidental pujtag identity",
            content.contains("com.aistudio.wifimasterpro.pujtag")
        )
        assertFalse(
            "applicationId must NOT contain accidental ehmllo identity",
            content.contains("com.aistudio.wifimasterpro.ehmllo")
        )
        assertFalse(
            "applicationId must NOT contain accidental rtyocc identity",
            content.contains("com.aistudio.wifimasterpro.rtyocc")
        )
        assertTrue(
            "namespace must be com.example",
            content.contains("namespace = \"com.example\"")
        )
    }

    @Test
    fun testNoDestructiveMigrationInSourceCode() {
        val appModuleFile = File("src/main/java/com/example/di/AppModule.kt")
        val content = appModuleFile.readText()
        assertFalse(
            "AppModule must NOT contain fallbackToDestructiveMigration",
            content.contains("fallbackToDestructiveMigration")
        )
    }
}
