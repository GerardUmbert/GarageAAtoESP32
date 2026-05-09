package com.dunnowsoftware.GarageAAtoESP32.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class DevicePreferences(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "garage_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var deviceAddress: String?
        get() = prefs.getString(KEY_ADDRESS, null)
        set(value) = prefs.edit().putString(KEY_ADDRESS, value).apply()

    var deviceName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var pin: String
        get() = prefs.getString(KEY_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()

    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO, value).apply()

    val isConfigured: Boolean
        get() = demoMode || (!deviceAddress.isNullOrEmpty() && pin.isNotEmpty())

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ADDRESS = "device_address"
        private const val KEY_NAME    = "device_name"
        private const val KEY_PIN     = "pin"
        private const val KEY_DEMO    = "demo_mode"
    }
}
