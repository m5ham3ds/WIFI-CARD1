package com.example.di

import androidx.room.Room
import com.example.data.local.database.AppDatabase
import com.example.data.local.preferences.AppPreferences
import com.example.data.local.preferences.ThemePreferences
import com.example.data.local.preferences.appDataStore
import com.example.data.local.preferences.themeDataStore
import com.example.data.repository.CardRepository
import com.example.data.repository.PatternRepository
import com.example.data.repository.RouterRepository
import com.example.data.repository.SessionRepository
import com.example.data.repository.TestResultRepository
import com.example.domain.repository.ICardRepository
import com.example.domain.repository.IPatternRepository
import com.example.domain.repository.IRouterRepository
import com.example.domain.repository.ISessionRepository
import com.example.domain.repository.ITestResultRepository
import com.example.domain.usecase.ExportResultsUseCase
import com.example.domain.usecase.GenerateCardsUseCase
import com.example.domain.usecase.ImportResultsUseCase
import com.example.domain.usecase.ManageRoutersUseCase
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val appModule = module {

    // Database
    single {
        Room.databaseBuilder(
            androidApplication(),
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onCreate(db)
                try {
                    db.execSQL(
                        "INSERT INTO router_profiles (name, ip, protocol, username, password, password_enabled, strategy_id, login_path, username_selector, password_selector, submit_selector, logout_selector, success_indicator, failure_indicator, is_active, is_default, created_at, auth_type, md5_salt) " +
                        "VALUES ('شبكة معتصم نت', 'wifi.sd.net', 'http', 'admin', '', 0, 'motasem', '/login', '#username, input[name=username]', 'input[name=password]', 'button[type=submit], .submit button', 'form[action*=\"logout\"], form[name=logout]', 'تفاصيل الأستخدام', 'خطأ', 1, 1, " + System.currentTimeMillis() + ", 'FORM', '')"
                    )
                    db.execSQL(
                        "INSERT INTO router_profiles (name, ip, protocol, username, password, password_enabled, strategy_id, login_path, username_selector, password_selector, submit_selector, logout_selector, success_indicator, failure_indicator, is_active, is_default, created_at, auth_type, md5_salt) " +
                        "VALUES ('بيلو', 'www.bello.com', 'http', 'admin', '', 0, 'bello', '/login', '#uname, input[name=username]', 'input[name=password]', '.submit button, button[type=submit]', 'form[action*=\"logout\"], form[name=logout]', 'id=\"card\"', 'خطأ', 1, 0, " + System.currentTimeMillis() + ", 'FORM', '')"
                    )
                    db.execSQL(
                        "INSERT INTO router_profiles (name, ip, protocol, username, password, password_enabled, strategy_id, login_path, username_selector, password_selector, submit_selector, logout_selector, success_indicator, failure_indicator, is_active, is_default, created_at, auth_type, md5_salt) " +
                        "VALUES ('شبكة الباشا', 'wifi.sd.net', 'http', 'admin', '', 0, 'abasha', '/login', 'input[name=username]', 'input[name=password]', 'input[type=submit], .button-submit', 'form[id=mForm]', 'MikroTicket Status', 'خطأ', 1, 0, " + System.currentTimeMillis() + ", 'FORM', '')"
                    )
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Failed to prepopulate default router in database onCreate")
                }
            }
        })
        .build()
    }

    // DAOs
    single { get<AppDatabase>().cardDao() }
    single { get<AppDatabase>().routerProfileDao() }
    single { get<AppDatabase>().testResultDao() }
    single { get<AppDatabase>().sessionDao() }
    single { get<AppDatabase>().patternDao() }

    // Preferences
    single { AppPreferences(androidApplication().appDataStore) }
    single { ThemePreferences(androidApplication().themeDataStore) }

    // Repositories
    single<ICardRepository> { CardRepository(get()) }
    single<IRouterRepository> { RouterRepository(get()) }
    single<ITestResultRepository> { TestResultRepository(get()) }
    single<ISessionRepository> { SessionRepository(get()) }
    single<IPatternRepository> { PatternRepository(get()) }

    // Use Cases
    factory { GenerateCardsUseCase(get()) }
    factory { ManageRoutersUseCase(get()) }
    factory { ExportResultsUseCase(get()) }
    factory { ImportResultsUseCase(get()) }
}
