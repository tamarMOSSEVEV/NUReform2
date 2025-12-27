package com.example.nureform

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.example.nureform.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber
import java.util.Locale

class NureformApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initTimber()
        initKoin()
        setLocale(this)
    }

    private fun initTimber() {
        Timber.Forest.plant(Timber.DebugTree())
    }

    private fun initKoin() {
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@NureformApplication)
            modules(appModule)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setLocale(this)
    }

    companion object {
        fun setLocale(context: Context) {
            val locale = Locale("he", "IL")
            Locale.setDefault(locale)

            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)

            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }
}