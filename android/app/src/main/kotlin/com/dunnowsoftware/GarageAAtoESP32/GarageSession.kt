package com.dunnowsoftware.GarageAAtoESP32

import androidx.car.app.Screen
import androidx.car.app.Session
import com.dunnowsoftware.GarageAAtoESP32.data.getSavedLocaleTag
import java.util.Locale

class GarageSession : Session() {
    override fun onCreateScreen(intent: android.content.Intent): Screen {
        applyLocale()
        return GarageScreen(carContext)
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
