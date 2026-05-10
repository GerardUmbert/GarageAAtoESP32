package com.dunnowsoftware.GarageAAtoESP32.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * Per-device pairing record. One ESP32 ↔ one password, bundled together so
 * the password can never get out of sync with the device it belongs to. Stored
 * inside EncryptedSharedPreferences so the password is at-rest encrypted and
 * unreadable from other apps.
 */
data class PairedDevice(
    val address: String,
    val name: String,
    val password: String,
)

class DevicePreferences(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "garage_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var pairedDevice: PairedDevice?
        get() {
            val raw = prefs.getString(KEY_PAIRED_DEVICE, null) ?: return null
            return try {
                val o = JSONObject(raw)
                PairedDevice(
                    address = o.getString("address"),
                    name = o.getString("name"),
                    password = o.getString("password"),
                )
            } catch (_: Throwable) {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_PAIRED_DEVICE).apply()
            } else {
                val json = JSONObject().apply {
                    put("address", value.address)
                    put("name", value.name)
                    put("password", value.password)
                }.toString()
                prefs.edit().putString(KEY_PAIRED_DEVICE, json).apply()
            }
        }

    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO, value).apply()

    var lastOpenedAt: Long
        get() = prefs.getLong(KEY_LAST_OPENED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_OPENED, value).apply()

    val hasPairedDevice: Boolean
        get() = pairedDevice != null

    val isConfigured: Boolean
        get() = demoMode || hasPairedDevice

    fun unpairDevice() {
        prefs.edit()
            .remove(KEY_PAIRED_DEVICE)
            .remove(KEY_LAST_OPENED)
            .apply()
    }

    fun updatePairedPassword(newPassword: String) {
        val current = pairedDevice ?: return
        pairedDevice = current.copy(password = newPassword)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_PAIRED_DEVICE = "paired_device"
        private const val KEY_DEMO          = "demo_mode"
        private const val KEY_LAST_OPENED   = "last_opened_at"
    }
}
