package com.example.presentation

import android.app.Application
import com.example.BuildConfig
import com.example.di.appModule
import com.example.di.viewModelModule
import com.example.util.AppLogger
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class MyApplication : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        val lang = com.example.util.LocaleHelper.getPersistedLocale(base)
        super.attachBaseContext(com.example.util.LocaleHelper.setLocale(base, lang))
    }

    override fun onCreate() {
        super.onCreate()
        
        // Timber logging (debug builds only to prevent logcat credential leaks)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("MyApplication onCreate called")
        }

        // App Logger
        AppLogger.init(this)

        // Koin DI Initialize
        startKoin {
            androidContext(this@MyApplication)
            modules(listOf(appModule, viewModelModule))
        }
    }
}
