package com.example.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.entity.*

@Database(
    entities = [
        CardEntity::class,
        RouterProfileEntity::class,
        TestResultEntity::class,
        TestSessionEntity::class,
        SuccessfulPatternEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun routerProfileDao(): RouterProfileDao
    abstract fun testResultDao(): TestResultDao
    abstract fun sessionDao(): SessionDao
    abstract fun patternDao(): PatternDao

    companion object {
        const val DATABASE_NAME = "wdmaster_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL(
                        "ALTER TABLE router_profiles ADD COLUMN logout_selector TEXT NOT NULL DEFAULT ''"
                    )
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // v2 to v3 schema alignment
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS `successful_patterns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `router_id` INTEGER NOT NULL, `pattern` TEXT NOT NULL, `confidence` REAL NOT NULL DEFAULT 0, `discovered_at` INTEGER NOT NULL, FOREIGN KEY(`router_id`) REFERENCES `router_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_successful_patterns_router_id` ON `successful_patterns` (`router_id`)"
                    )
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("PRAGMA table_info(router_profiles)")
                var hasColumn = false
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1 && cursor.getString(nameIndex) == "password_enabled") {
                        hasColumn = true
                        break
                    }
                }
                cursor.close()
                if (!hasColumn) {
                    database.execSQL(
                        "ALTER TABLE router_profiles ADD COLUMN password_enabled INTEGER NOT NULL DEFAULT 0"
                    )
                }

                // Phase 3.1: Migrate any legacy plaintext credentials to encrypted ciphertext
                try {
                    val profileCursor = database.query("SELECT id, password FROM router_profiles")
                    val updates = mutableListOf<Pair<Long, String>>()
                    while (profileCursor.moveToNext()) {
                        val idIndex = profileCursor.getColumnIndex("id")
                        val pwdIndex = profileCursor.getColumnIndex("password")
                        if (idIndex != -1 && pwdIndex != -1) {
                            val id = profileCursor.getLong(idIndex)
                            val pwd = profileCursor.getString(pwdIndex) ?: ""
                            if (pwd.isNotBlank() && !com.example.util.SecurityUtils.isEncrypted(pwd)) {
                                val encrypted = com.example.util.SecurityUtils.encryptPasswordAtRest(pwd)
                                updates.add(Pair(id, encrypted))
                            }
                        }
                    }
                    profileCursor.close()

                    for ((id, encryptedPwd) in updates) {
                        database.execSQL(
                            "UPDATE router_profiles SET password = ? WHERE id = ?",
                            arrayOf<Any>(encryptedPwd, id)
                        )
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Error migrating legacy plaintext credentials")
                }
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("PRAGMA table_info(router_profiles)")
                var hasColumn = false
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1 && cursor.getString(nameIndex) == "strategy_id") {
                        hasColumn = true
                        break
                    }
                }
                cursor.close()
                if (!hasColumn) {
                    database.execSQL(
                        "ALTER TABLE router_profiles ADD COLUMN strategy_id TEXT NOT NULL DEFAULT 'generic'"
                    )
                }

                // Deterministic strategy identity population & runtime host correction
                try {
                    // ALBASHA: strategyId = 'abasha', runtime host = 'wifi.sd.net'
                    database.execSQL("UPDATE router_profiles SET strategy_id = 'abasha' WHERE name LIKE '%الباشا%' OR name LIKE '%abasha%'")
                    database.execSQL("UPDATE router_profiles SET ip = 'wifi.sd.net' WHERE (name LIKE '%الباشا%' OR name LIKE '%abasha%') AND ip = 'www.Abasha.com'")

                    // MOTASEM: strategyId = 'motasem', runtime host = 'wifi.sd.net'
                    database.execSQL("UPDATE router_profiles SET strategy_id = 'motasem' WHERE name LIKE '%معتصم%' OR name LIKE '%motasem%'")
                    database.execSQL("UPDATE router_profiles SET ip = 'wifi.sd.net' WHERE (name LIKE '%معتصم%' OR name LIKE '%motasem%') AND ip = 'r.com'")

                    // BELLO: strategyId = 'bello', runtime host = 'www.bello.com'
                    database.execSQL("UPDATE router_profiles SET strategy_id = 'bello' WHERE name LIKE '%بيلو%' OR name LIKE '%bello%'")
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Error migrating strategy_id and runtime hosts in MIGRATION_5_6")
                }
            }
        }

        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(database)
                MIGRATION_2_3.migrate(database)
                MIGRATION_3_4.migrate(database)
                MIGRATION_4_5.migrate(database)
            }
        }

        val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(database)
                MIGRATION_3_4.migrate(database)
                MIGRATION_4_5.migrate(database)
            }
        }

        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_3_4.migrate(database)
                MIGRATION_4_5.migrate(database)
            }
        }

        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(database)
                MIGRATION_2_3.migrate(database)
                MIGRATION_3_4.migrate(database)
                MIGRATION_4_5.migrate(database)
                MIGRATION_5_6.migrate(database)
            }
        }

        val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(database)
                MIGRATION_3_4.migrate(database)
                MIGRATION_4_5.migrate(database)
                MIGRATION_5_6.migrate(database)
            }
        }

        val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_3_4.migrate(database)
                MIGRATION_4_5.migrate(database)
                MIGRATION_5_6.migrate(database)
            }
        }

        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_4_5.migrate(database)
                MIGRATION_5_6.migrate(database)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    val cursor = database.query("PRAGMA table_info(test_results)")
                    var hasCategory = false
                    var hasSubReason = false
                    var hasSuccessReason = false
                    while (cursor.moveToNext()) {
                        val nameIndex = cursor.getColumnIndex("name")
                        if (nameIndex != -1) {
                            val colName = cursor.getString(nameIndex)
                            if (colName == "category") hasCategory = true
                            if (colName == "subReason") hasSubReason = true
                            if (colName == "successReason") hasSuccessReason = true
                        }
                    }
                    cursor.close()

                    if (!hasCategory) {
                        database.execSQL("ALTER TABLE test_results ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                    }
                    if (!hasSubReason) {
                        database.execSQL("ALTER TABLE test_results ADD COLUMN subReason TEXT NOT NULL DEFAULT ''")
                    }
                    if (!hasSuccessReason) {
                        database.execSQL("ALTER TABLE test_results ADD COLUMN successReason TEXT")
                    }

                    // Backfill category, subReason, and successReason from legacy records
                    database.execSQL("""
                        UPDATE test_results SET 
                            category = CASE 
                                WHEN state = 'Success' THEN 'SUCCESS'
                                WHEN state = 'Timeout' THEN 'TIMEOUT'
                                WHEN state = 'Network_Error' THEN 'NETWORK_ERROR'
                                WHEN state = 'Engine_Error' THEN 'ENGINE_ERROR'
                                ELSE 'FAILURE'
                            END
                        WHERE category = '' OR category IS NULL
                    """)

                    database.execSQL("""
                        UPDATE test_results SET 
                            subReason = CASE 
                                WHEN state = 'Success' AND (message LIKE '%جهازين%' OR message LIKE '%TWO_DEVICES%' OR message LIKE '%جهازان%') THEN 'TWO_DEVICES_SUCCESS'
                                WHEN state = 'Success' THEN 'NORMAL_LOGIN_SUCCESS'
                                WHEN state = 'Timeout' THEN 'NETWORK_TIMEOUT'
                                WHEN state = 'Network_Error' AND (message LIKE '%DNS%' OR message LIKE '%عنوان%') THEN 'DNS_ERROR'
                                WHEN state = 'Network_Error' THEN 'WEBVIEW_ERROR'
                                WHEN state = 'Engine_Error' THEN 'UNKNOWN'
                                WHEN message LIKE '%منتهي%' OR message LIKE '%expired%' THEN 'EXPIRED_CARD'
                                WHEN message LIKE '%رصيد%' OR message LIKE '%balance%' OR message LIKE '%quota%' OR message LIKE '%نفذ%' THEN 'INSUFFICIENT_BALANCE'
                                WHEN message LIKE '%غير صحيح%' OR message LIKE '%invalid%' OR message LIKE '%not found%' THEN 'INVALID_CARD'
                                ELSE 'PORTAL_REJECTED'
                            END
                        WHERE subReason = '' OR subReason IS NULL
                    """)

                    database.execSQL("""
                        UPDATE test_results SET 
                            successReason = CASE 
                                WHEN state = 'Success' AND (message LIKE '%جهازين%' OR message LIKE '%TWO_DEVICES%' OR message LIKE '%جهازان%') THEN 'TWO_DEVICES_ACTIVE_CARD'
                                WHEN state = 'Success' THEN 'NORMAL_LOGIN'
                                ELSE NULL
                            END
                        WHERE successReason IS NULL AND state = 'Success'
                    """)
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Error executing MIGRATION_6_7")
                }
            }
        }

        val MIGRATION_1_7 = object : Migration(1, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_1_6.migrate(database)
                MIGRATION_6_7.migrate(database)
            }
        }

        val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_5_6.migrate(database)
                MIGRATION_6_7.migrate(database)
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_1_5,
            MIGRATION_2_5,
            MIGRATION_3_5,
            MIGRATION_1_6,
            MIGRATION_2_6,
            MIGRATION_3_6,
            MIGRATION_4_6,
            MIGRATION_1_7,
            MIGRATION_5_7
        )
    }
}
