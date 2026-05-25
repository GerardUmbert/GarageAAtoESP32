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
 *
 * geofenceLat/Lng/RadiusM are null when no geofence has been configured.
 * geofenceEnabled only has effect when the triple is non-null.
 */
data class PairedDevice(
    val address: String,
    val name: String,
    val password: String,
    val geofenceLat: Double? = null,
    val geofenceLng: Double? = null,
    val geofenceRadiusM: Float? = null,
    val geofenceEnabled: Boolean = false,
) {
    val hasGeofence: Boolean
        get() = geofenceLat != null && geofenceLng != null && geofenceRadiusM != null

    val isGeofenceActive: Boolean
        get() = hasGeofence && geofenceEnabled
}

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
                    geofenceLat = if (o.has("geofence_lat")) o.getDouble("geofence_lat") else null,
                    geofenceLng = if (o.has("geofence_lng")) o.getDouble("geofence_lng") else null,
                    geofenceRadiusM = if (o.has("geofence_radius_m")) o.getDouble("geofence_radius_m").toFloat() else null,
                    geofenceEnabled = o.optBoolean("geofence_enabled", false),
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
                    if (value.geofenceLat != null) put("geofence_lat", value.geofenceLat)
                    if (value.geofenceLng != null) put("geofence_lng", value.geofenceLng)
                    if (value.geofenceRadiusM != null) put("geofence_radius_m", value.geofenceRadiusM.toDouble())
                    put("geofence_enabled", value.geofenceEnabled)
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

    /**
     * Epoch-ms of the last successful auto-fire (geofence or BLE in-range).
     * Shared between GarageScreen (AA process) and GeofenceBroadcastReceiver
     * (main process) via EncryptedSharedPreferences — the only storage that
     * crosses the process boundary reliably.
     */
    var lastAutoFiredAt: Long
        get() = prefs.getLong(KEY_LAST_AUTO_FIRED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_AUTO_FIRED, value).apply()

    /**
     * Epoch-ms of the last time Android Auto was observed connected.
     * Written by GarageSession on start/stop so the broadcast receiver
     * (different process) can check the 60s grace window.
     */
    var lastCarConnectionAt: Long
        get() = prefs.getLong(KEY_LAST_CAR_CONNECTION, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CAR_CONNECTION, value).apply()

    val hasPairedDevice: Boolean
        get() = pairedDevice != null

    val isConfigured: Boolean
        get() = demoMode || hasPairedDevice

    fun unpairDevice() {
        prefs.edit()
            .remove(KEY_PAIRED_DEVICE)
            .remove(KEY_LAST_OPENED)
            .remove(KEY_LAST_AUTO_FIRED)
            .apply()
    }

    fun updatePairedPassword(newPassword: String) {
        val current = pairedDevice ?: return
        pairedDevice = current.copy(password = newPassword)
    }

    fun updateGeofence(lat: Double, lng: Double, radiusM: Float) {
        val current = pairedDevice ?: return
        pairedDevice = current.copy(
            geofenceLat = lat,
            geofenceLng = lng,
            geofenceRadiusM = radiusM,
        )
    }

    fun clearGeofence() {
        val current = pairedDevice ?: return
        pairedDevice = current.copy(
            geofenceLat = null,
            geofenceLng = null,
            geofenceRadiusM = null,
            geofenceEnabled = false,
        )
    }

    fun setGeofenceEnabled(enabled: Boolean) {
        val current = pairedDevice ?: return
        pairedDevice = current.copy(geofenceEnabled = enabled)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_PAIRED_DEVICE     = "paired_device"
        private const val KEY_DEMO              = "demo_mode"
        private const val KEY_LAST_OPENED       = "last_opened_at"
        private const val KEY_LAST_AUTO_FIRED   = "last_auto_fired_at"
        private const val KEY_LAST_CAR_CONNECTION = "last_car_connection_at"
    }
}
