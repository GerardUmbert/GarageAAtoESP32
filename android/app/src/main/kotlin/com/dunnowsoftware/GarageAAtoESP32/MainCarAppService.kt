package com.dunnowsoftware.GarageAAtoESP32

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.dunnowsoftware.GarageAAtoESP32.data.getSavedLocaleTag
import com.dunnowsoftware.GarageAAtoESP32.data.localeListFromTag

class MainCarAppService : CarAppService() {

    override fun attachBaseContext(newBase: Context) {
        val tag = getSavedLocaleTag(newBase)
        AppCompatDelegate.setApplicationLocales(localeListFromTag(tag))
        super.attachBaseContext(newBase)
    }

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = GarageSession()
}
