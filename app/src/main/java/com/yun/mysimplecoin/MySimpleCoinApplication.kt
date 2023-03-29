package com.yun.mysimplecoin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MySimpleCoinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}