package com.contextos.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ContextOSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
