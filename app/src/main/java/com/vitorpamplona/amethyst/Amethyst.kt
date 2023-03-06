package com.vitorpamplona.amethyst

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

class Amethyst : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        instance = this
        WorkManager.initialize(this, workManagerConfiguration)
    }

    companion object {
        lateinit var instance: Amethyst
            private set
    }

    override fun getWorkManagerConfiguration() = Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.INFO)
        .build()
}
