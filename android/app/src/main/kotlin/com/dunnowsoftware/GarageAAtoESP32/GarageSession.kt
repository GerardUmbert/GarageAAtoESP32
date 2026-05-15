package com.dunnowsoftware.GarageAAtoESP32

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.dunnowsoftware.GarageAAtoESP32.data.getSavedLocaleTag
import java.util.Locale

class GarageSession : Session() {

    private var garageScreen: GarageScreen? = null

    override fun onCreateScreen(intent: Intent): Screen {
        applyLocale()
        return GarageScreen(carContext).also { garageScreen = it }
    }

    override fun onNewIntent(intent: Intent) {
        if (intent.getBooleanExtra("voice_open", false)) {
            garageScreen?.onVoiceOpen()
        }
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
