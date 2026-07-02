package com.dunnowsoftware.GarageAAtoESP32.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * Common geofence fields shared by both pairing types (BLE device, webhook
 * config) so geofence consumers (GeofenceBroadcastReceiver,
 * GeofenceForegroundService, the geofence picker UI) can operate on whichever
 * transport is active without branching on transport type.
 */
interface GeofenceCapable {
    val geofenceLat: Double?
    val geofenceLng: Double?
    val geofenceRadiusM: Float?
    val geofenceOuterOffsetM: Float
    val geofenceEnabled: Boolean

    val hasGeofence: Boolean
        get() = geofenceLat != null && geofenceLng != null && geofenceRadiusM != null

    val isGeofenceActive: Boolean
        get() = hasGeofence && geofenceEnabled
}

enum class TransportType { BLE, WEBHOOK }

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
    override val geofenceLat: Double? = null,
    override val geofenceLng: Double? = null,
    override val geofenceRadiusM: Float? = null,
    override val geofenceOuterOffsetM: Float = 400f,
    override val geofenceEnabled: Boolean = false,
    val hasWebLog: Boolean = false,
) : GeofenceCapable

/**
 * Webhook transport config — an alternative to a BLE pairing. "Open" means an
 * HTTP POST to `url` (e.g. a Home Assistant native webhook trigger or
 * rest_command endpoint) instead of a BLE connection to an ESP32.
 *
 * authToken is optional: Home Assistant's native webhook trigger
 * (`/api/webhook/<webhook_id>`) needs only the URL — the webhook_id itself is
 * the secret, no Authorization header involved. Bearer-token endpoints (this
 * project's own firmware HA mode, or a custom script) fill this in.
 */
data class WebhookConfig(
    val url: String,
    val authToken: String? = null,
    val name: String,
    override val geofenceLat: Double? = null,
    override val geofenceLng: Double? = null,
    override val geofenceRadiusM: Float? = null,
    override val geofenceOuterOffsetM: Float = 400f,
    override val geofenceEnabled: Boolean = false,
) : GeofenceCapable

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
                    geofenceOuterOffsetM = if (o.has("geofence_outer_offset_m")) o.getDouble("geofence_outer_offset_m").toFloat() else 400f,
                    geofenceEnabled = o.optBoolean("geofence_enabled", false),
                    hasWebLog = o.optBoolean("has_web_log", false),
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
                    put("geofence_outer_offset_m", value.geofenceOuterOffsetM.toDouble())
                    put("geofence_enabled", value.geofenceEnabled)
                    put("has_web_log", value.hasWebLog)
                }.toString()
                // Mutual exclusion: pairing a BLE device supersedes any webhook config.
                prefs.edit()
                    .putString(KEY_PAIRED_DEVICE, json)
                    .remove(KEY_WEBHOOK_CONFIG)
                    .apply()
            }
        }

    var webhookConfig: WebhookConfig?
        get() {
            val raw = prefs.getString(KEY_WEBHOOK_CONFIG, null) ?: return null
            return try {
                val o = JSONObject(raw)
                WebhookConfig(
                    url = o.getString("url"),
                    authToken = o.optString("auth_token").takeIf { it.isNotEmpty() },
                    name = o.getString("name"),
                    geofenceLat = if (o.has("geofence_lat")) o.getDouble("geofence_lat") else null,
                    geofenceLng = if (o.has("geofence_lng")) o.getDouble("geofence_lng") else null,
                    geofenceRadiusM = if (o.has("geofence_radius_m")) o.getDouble("geofence_radius_m").toFloat() else null,
                    geofenceOuterOffsetM = if (o.has("geofence_outer_offset_m")) o.getDouble("geofence_outer_offset_m").toFloat() else 400f,
                    geofenceEnabled = o.optBoolean("geofence_enabled", false),
                )
            } catch (_: Throwable) {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_WEBHOOK_CONFIG).apply()
            } else {
                val json = JSONObject().apply {
                    put("url", value.url)
                    if (!value.authToken.isNullOrBlank()) put("auth_token", value.authToken)
                    put("name", value.name)
                    if (value.geofenceLat != null) put("geofence_lat", value.geofenceLat)
                    if (value.geofenceLng != null) put("geofence_lng", value.geofenceLng)
                    if (value.geofenceRadiusM != null) put("geofence_radius_m", value.geofenceRadiusM.toDouble())
                    put("geofence_outer_offset_m", value.geofenceOuterOffsetM.toDouble())
                    put("geofence_enabled", value.geofenceEnabled)
                }.toString()
                // Mutual exclusion: configuring a webhook supersedes any BLE pairing.
                prefs.edit()
                    .putString(KEY_WEBHOOK_CONFIG, json)
                    .remove(KEY_PAIRED_DEVICE)
                    .apply()
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

    var lastAutoFailedAt: Long
        get() = prefs.getLong(KEY_LAST_AUTO_FAILED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_AUTO_FAILED, value).apply()

    var lastSendingAt: Long
        get() = prefs.getLong(KEY_LAST_SENDING, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SENDING, value).apply()

    // Set to true when outer geofence EXIT fires. Used as an optimistic hint
    // on the next outer ENTER — if false and activity is UNKNOWN, warmup is
    // skipped (likely still at home). If EXIT was dropped by the OS this stays
    // false, but IN_VEHICLE/ON_BICYCLE activity still overrides it.
    var wasOutsideOuterGeofence: Boolean
        get() = prefs.getBoolean(KEY_OUTSIDE_OUTER_GEOFENCE, false)
        set(value) = prefs.edit().putBoolean(KEY_OUTSIDE_OUTER_GEOFENCE, value).apply()

    val hasPairedDevice: Boolean
        get() = pairedDevice != null

    val hasWebhookConfig: Boolean
        get() = webhookConfig != null

    val activeTransportType: TransportType?
        get() = when {
            hasPairedDevice   -> TransportType.BLE
            hasWebhookConfig  -> TransportType.WEBHOOK
            else              -> null
        }

    /** Geofence-capable record for whichever transport is currently active, or null if neither is configured. */
    val activeGeofenceCapable: GeofenceCapable?
        get() = pairedDevice ?: webhookConfig

    val isConfigured: Boolean
        get() = demoMode || hasPairedDevice || hasWebhookConfig

    fun unpairDevice() {
        prefs.edit()
            .remove(KEY_PAIRED_DEVICE)
            .remove(KEY_LAST_OPENED)
            .remove(KEY_LAST_AUTO_FIRED)
            .apply()
    }

    fun clearWebhookConfig() {
        prefs.edit()
            .remove(KEY_WEBHOOK_CONFIG)
            .remove(KEY_LAST_OPENED)
            .remove(KEY_LAST_AUTO_FIRED)
            .apply()
    }

    fun updatePairedPassword(newPassword: String) {
        val current = pairedDevice ?: return
        pairedDevice = current.copy(password = newPassword)
    }

    fun updateGeofence(lat: Double, lng: Double, radiusM: Float, outerOffsetM: Float) {
        val current = pairedDevice ?: return
        pairedDevice = current.copy(
            geofenceLat = lat,
            geofenceLng = lng,
            geofenceRadiusM = radiusM,
            geofenceOuterOffsetM = outerOffsetM,
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

    fun updateWebhookGeofence(lat: Double, lng: Double, radiusM: Float, outerOffsetM: Float) {
        val current = webhookConfig ?: return
        webhookConfig = current.copy(
            geofenceLat = lat,
            geofenceLng = lng,
            geofenceRadiusM = radiusM,
            geofenceOuterOffsetM = outerOffsetM,
        )
    }

    fun clearWebhookGeofence() {
        val current = webhookConfig ?: return
        webhookConfig = current.copy(
            geofenceLat = null,
            geofenceLng = null,
            geofenceRadiusM = null,
            geofenceEnabled = false,
        )
    }

    fun setWebhookGeofenceEnabled(enabled: Boolean) {
        val current = webhookConfig ?: return
        webhookConfig = current.copy(geofenceEnabled = enabled)
    }

    /**
     * Update geofence fields on whichever transport is currently active.
     * No-op if neither a BLE device nor a webhook is configured.
     */
    fun updateActiveGeofence(lat: Double, lng: Double, radiusM: Float, outerOffsetM: Float) {
        when (activeTransportType) {
            TransportType.BLE     -> updateGeofence(lat, lng, radiusM, outerOffsetM)
            TransportType.WEBHOOK -> updateWebhookGeofence(lat, lng, radiusM, outerOffsetM)
            null                  -> Unit
        }
    }

    fun clearActiveGeofence() {
        when (activeTransportType) {
            TransportType.BLE     -> clearGeofence()
            TransportType.WEBHOOK -> clearWebhookGeofence()
            null                  -> Unit
        }
    }

    fun setActiveGeofenceEnabled(enabled: Boolean) {
        when (activeTransportType) {
            TransportType.BLE     -> setGeofenceEnabled(enabled)
            TransportType.WEBHOOK -> setWebhookGeofenceEnabled(enabled)
            null                  -> Unit
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_PAIRED_DEVICE          = "paired_device"
        private const val KEY_WEBHOOK_CONFIG          = "webhook_config"
        private const val KEY_DEMO                   = "demo_mode"
        private const val KEY_LAST_OPENED            = "last_opened_at"
        private const val KEY_LAST_AUTO_FIRED        = "last_auto_fired_at"
        private const val KEY_LAST_AUTO_FAILED       = "last_auto_failed_at"
        private const val KEY_LAST_SENDING           = "last_sending_at"
        private const val KEY_OUTSIDE_OUTER_GEOFENCE = "outside_outer_geofence"
    }
}
