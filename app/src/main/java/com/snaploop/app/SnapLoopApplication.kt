package com.snaploop.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.snaploop.app.analytics.SnapLoopTelemetry
import com.snaploop.app.analytics.TelemetryEvent

class SnapLoopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FIREBASE_CONFIG_PRESENT) {
            FirebaseApp.initializeApp(this)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
        SnapLoopTelemetry.initialize(this)
        SnapLoopTelemetry.capture(TelemetryEvent.appOpened())
    }
}
