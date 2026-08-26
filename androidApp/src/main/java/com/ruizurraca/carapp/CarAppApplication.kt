package com.ruizurraca.carapp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

class CarAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck
            .getInstance()
            .installAppCheckProviderFactory(appCheckProviderFactory())
    }
}
