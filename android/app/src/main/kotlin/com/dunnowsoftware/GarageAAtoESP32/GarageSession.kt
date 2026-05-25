package com.dunnowsoftware.GarageAAtoESP32

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.getSavedLocaleTag
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceLogger
import java.util.Locale

private const val TAG = "GarageSession"

class GarageSession : Session() {

    private var garageScreen: GarageScreen? = null

    override fun onCreateScreen(intent: Intent): Screen {
        applyLocale()
        stampCarConnection()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner)  = stampCarConnection()
            override fun onStop(owner: LifecycleOwner)   = stampCarConnection()
        })
        return GarageScreen(carContext).also { garageScreen = it }
    }

    override fun onNewIntent(intent: Intent) {
        if (intent.getBooleanExtra("voice_open", false)) {
            garageScreen?.onVoiceOpen()
        }
    }

    // Write a timestamp every time AA connects or reconnects so the broadcast
    // receiver (different process) can check the 60s grace window.
    private fun stampCarConnection() {
        val ts = System.currentTimeMillis()
        GeofenceLogger.i(carContext, TAG, "Stamping lastCarConnectionAt=$ts")
        DevicePreferences(carContext).lastCarConnectionAt = ts
    }

    private fun applyLocale() {
        val tag = getSavedLocaleTag(carContext) ?: return
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = carContext.resources.configuration
        config.setLocale(locale)
        carContext.resources.updateConfiguration(config, carContext.resources.displayMetrics)
    }
}
