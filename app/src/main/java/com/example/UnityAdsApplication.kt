package com.example

import android.app.Application
import android.util.Log

/**
 * Custom Application class that triggers Unity Ads initialization
 * as soon as the application process starts.
 */
class UnityAdsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("UnityAdsApplication", "Application started. Initializing Unity Ads...")
        UnityAdsManager.initialize(this)
    }
}
