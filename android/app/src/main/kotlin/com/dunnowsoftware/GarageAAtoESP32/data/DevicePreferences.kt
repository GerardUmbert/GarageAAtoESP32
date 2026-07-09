package com.dunnowsoftware.GarageAAtoESP32.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Common geofence fields shared by every device (BLE or webhook) so geofence
 * consumers (GeofenceBroadcastReceiver, GeofenceForegroundService, the
 * geofence picker UI) can operate on any device without branching on
 * transport type.
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
 * BLE pairing details. One ESP32 ↔ one password, bundled together so the
 * password can never get out of sync with the device it belongs to.
 */
data class PairedDevice(
    val address: String,
    val name: String,
    val password: String,
    val hasWebLog: Boolean = false,
)

/**
 * Webhook transport details — an alternative to a BLE pairing. "Open" means
 * an HTTP POST to `url` (e.g. a Home Assistant native webhook trigger or
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
)

/**
 * A single paired garage opener — either BLE or webhook, never both. Geofence
 * fields live here (one geofence per device) rather than on [PairedDevice]/
 * [WebhookConfig], since a device's location doesn't depend on which
 * transport it uses.
 *
 * Construction is only possible through [ble]/[webhook] so an invalid mixed
 * state (both or neither of the transport-specific fields set) can't exist.
 */
data class GarageDevice private constructor(
    val id: String,
    val transport: TransportType,
    val ble: PairedDevice?,
    val webhook: WebhookConfig?,
    override val geofenceLat: Double? = null,
    override val geofenceLng: Double? = null,
    override val geofenceRadiusM: Float? = null,
    override val geofenceOuterOffsetM: Float = 400f,
    override val geofenceEnabled: Boolean = false,
) : GeofenceCapable {

    val name: String get() = ble?.name ?: webhook?.name ?: ""

    /** Stable key for geofence registration and transport lookup: the BLE MAC, or this device's id for webhooks. */
    val addressKey: String get() = ble?.address ?: id

    /** Returns a copy with [ble] replaced. Only valid on a BLE device — no-op (returns this) otherwise. */
    fun withBle(updated: PairedDevice): GarageDevice = if (transport == TransportType.BLE) copy(ble = updated) else this

    /** Returns a copy with [webhook] replaced. Only valid on a webhook device — no-op (returns this) otherwise. */
    fun withWebhook(updated: WebhookConfig): GarageDevice = if (transport == TransportType.WEBHOOK) copy(webhook = updated) else this

    fun withGeofence(lat: Double, lng: Double, radiusM: Float, outerOffsetM: Float): GarageDevice = copy(
        geofenceLat = lat,
        geofenceLng = lng,
        geofenceRadiusM = radiusM,
        geofenceOuterOffsetM = outerOffsetM,
    )

    fun withGeofenceCleared(): GarageDevice = copy(
        geofenceLat = null,
        geofenceLng = null,
        geofenceRadiusM = null,
        geofenceEnabled = false,
    )

    fun withGeofenceEnabled(enabled: Boolean): GarageDevice = copy(geofenceEnabled = enabled)

    companion object {
        fun ble(id: String = UUID.randomUUID().toString(), device: PairedDevice) =
            GarageDevice(id = id, transport = TransportType.BLE, ble = device, webhook = null)

        fun webhook(id: String = UUID.randomUUID().toString(), config: WebhookConfig) =
            GarageDevice(id = id, transport = TransportType.WEBHOOK, ble = null, webhook = config)
    }
}

class DevicePreferences(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "garage_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init {
        migrateLegacySingleDeviceIfNeeded()
    }

    var devices: List<GarageDevice>
        get() {
            val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i -> deviceFromJson(arr.getJSONObject(i)) }
            } catch (_: Throwable) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(deviceToJson(it)) }
            prefs.edit().putString(KEY_DEVICES, arr.toString()).apply()
        }

    /** Which [GarageDevice.id] every trigger surface should fire. Falls back to the sole device, or the most recently added, when unset. */
    var selectedDeviceId: String?
        get() {
            val stored = prefs.getString(KEY_SELECTED_DEVICE_ID, null)
            val list = devices
            if (stored != null && list.any { it.id == stored }) return stored
            return list.lastOrNull()?.id
        }
        set(value) {
            if (value == null) prefs.edit().remove(KEY_SELECTED_DEVICE_ID).apply()
            else prefs.edit().putString(KEY_SELECTED_DEVICE_ID, value).apply()
        }

    val selectedDevice: GarageDevice?
        get() = selectedDeviceId?.let { id -> devices.firstOrNull { it.id == id } }

    fun device(id: String): GarageDevice? = devices.firstOrNull { it.id == id }

    fun addDevice(device: GarageDevice) {
        devices = devices + device
        selectedDeviceId = device.id
    }

    fun updateDevice(updated: GarageDevice) {
        devices = devices.map { if (it.id == updated.id) updated else it }
    }

    fun removeDevice(id: String) {
        devices = devices.filterNot { it.id == id }
        if (selectedDeviceId == id) {
            prefs.edit().remove(KEY_SELECTED_DEVICE_ID).apply()
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

    val isConfigured: Boolean
        get() = demoMode || devices.isNotEmpty()

    fun updateGeofence(deviceId: String, lat: Double, lng: Double, radiusM: Float, outerOffsetM: Float) {
        val current = device(deviceId) ?: return
        updateDevice(
            current.copy(
                geofenceLat = lat,
                geofenceLng = lng,
                geofenceRadiusM = radiusM,
                geofenceOuterOffsetM = outerOffsetM,
            )
        )
    }

    fun clearGeofence(deviceId: String) {
        val current = device(deviceId) ?: return
        updateDevice(
            current.copy(
                geofenceLat = null,
                geofenceLng = null,
                geofenceRadiusM = null,
                geofenceEnabled = false,
            )
        )
    }

    fun setGeofenceEnabled(deviceId: String, enabled: Boolean) {
        val current = device(deviceId) ?: return
        updateDevice(current.copy(geofenceEnabled = enabled))
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    // ── JSON (de)serialization ───────────────────────────────────────────────

    private fun deviceToJson(d: GarageDevice): JSONObject = JSONObject().apply {
        put("id", d.id)
        put("transport", d.transport.name)
        d.ble?.let { ble ->
            put("address", ble.address)
            put("name", ble.name)
            put("password", ble.password)
            put("has_web_log", ble.hasWebLog)
        }
        d.webhook?.let { webhook ->
            put("url", webhook.url)
            if (!webhook.authToken.isNullOrBlank()) put("auth_token", webhook.authToken)
            put("name", webhook.name)
        }
        if (d.geofenceLat != null) put("geofence_lat", d.geofenceLat)
        if (d.geofenceLng != null) put("geofence_lng", d.geofenceLng)
        if (d.geofenceRadiusM != null) put("geofence_radius_m", d.geofenceRadiusM.toDouble())
        put("geofence_outer_offset_m", d.geofenceOuterOffsetM.toDouble())
        put("geofence_enabled", d.geofenceEnabled)
    }

    private fun deviceFromJson(o: JSONObject): GarageDevice? = try {
        val id = o.getString("id")
        val transport = TransportType.valueOf(o.getString("transport"))
        val base = when (transport) {
            TransportType.BLE -> GarageDevice.ble(
                id = id,
                device = PairedDevice(
                    address = o.getString("address"),
                    name = o.getString("name"),
                    password = o.getString("password"),
                    hasWebLog = o.optBoolean("has_web_log", false),
                ),
            )
            TransportType.WEBHOOK -> GarageDevice.webhook(
                id = id,
                config = WebhookConfig(
                    url = o.getString("url"),
                    authToken = o.optString("auth_token").takeIf { it.isNotEmpty() },
                    name = o.getString("name"),
                ),
            )
        }
        base.copy(
            geofenceLat = if (o.has("geofence_lat")) o.getDouble("geofence_lat") else null,
            geofenceLng = if (o.has("geofence_lng")) o.getDouble("geofence_lng") else null,
            geofenceRadiusM = if (o.has("geofence_radius_m")) o.getDouble("geofence_radius_m").toFloat() else null,
            geofenceOuterOffsetM = if (o.has("geofence_outer_offset_m")) o.getDouble("geofence_outer_offset_m").toFloat() else 400f,
            geofenceEnabled = o.optBoolean("geofence_enabled", false),
        )
    } catch (_: Throwable) {
        null
    }

    // ── One-time migration from the old single-slot schema ──────────────────

    private fun migrateLegacySingleDeviceIfNeeded() {
        if (prefs.contains(KEY_DEVICES)) return // already migrated (or a fresh install with an empty list already written)
        val legacyBle = prefs.getString(KEY_PAIRED_DEVICE_LEGACY, null)
        val legacyWebhook = prefs.getString(KEY_WEBHOOK_CONFIG_LEGACY, null)
        if (legacyBle == null && legacyWebhook == null) {
            // Fresh install: write an empty list so this branch doesn't re-run.
            prefs.edit().putString(KEY_DEVICES, JSONArray().toString()).apply()
            return
        }

        val migrated = mutableListOf<GarageDevice>()
        try {
            if (legacyBle != null) {
                val o = JSONObject(legacyBle)
                val device = GarageDevice.ble(
                    device = PairedDevice(
                        address = o.getString("address"),
                        name = o.getString("name"),
                        password = o.getString("password"),
                        hasWebLog = o.optBoolean("has_web_log", false),
                    ),
                ).copy(
                    geofenceLat = if (o.has("geofence_lat")) o.getDouble("geofence_lat") else null,
                    geofenceLng = if (o.has("geofence_lng")) o.getDouble("geofence_lng") else null,
                    geofenceRadiusM = if (o.has("geofence_radius_m")) o.getDouble("geofence_radius_m").toFloat() else null,
                    geofenceOuterOffsetM = if (o.has("geofence_outer_offset_m")) o.getDouble("geofence_outer_offset_m").toFloat() else 400f,
                    geofenceEnabled = o.optBoolean("geofence_enabled", false),
                )
                migrated += device
            } else if (legacyWebhook != null) {
                val o = JSONObject(legacyWebhook)
                val device = GarageDevice.webhook(
                    config = WebhookConfig(
                        url = o.getString("url"),
                        authToken = o.optString("auth_token").takeIf { it.isNotEmpty() },
                        name = o.getString("name"),
                    ),
                ).copy(
                    geofenceLat = if (o.has("geofence_lat")) o.getDouble("geofence_lat") else null,
                    geofenceLng = if (o.has("geofence_lng")) o.getDouble("geofence_lng") else null,
                    geofenceRadiusM = if (o.has("geofence_radius_m")) o.getDouble("geofence_radius_m").toFloat() else null,
                    geofenceOuterOffsetM = if (o.has("geofence_outer_offset_m")) o.getDouble("geofence_outer_offset_m").toFloat() else 400f,
                    geofenceEnabled = o.optBoolean("geofence_enabled", false),
                )
                migrated += device
            }
        } catch (_: Throwable) {
            // Corrupt legacy blob — proceed with whatever migrated cleanly (possibly nothing)
            // rather than lose the user's whole config to one bad field.
        }

        val arr = JSONArray()
        migrated.forEach { arr.put(deviceToJson(it)) }
        val editor = prefs.edit()
            .putString(KEY_DEVICES, arr.toString())
            .remove(KEY_PAIRED_DEVICE_LEGACY)
            .remove(KEY_WEBHOOK_CONFIG_LEGACY)
        migrated.firstOrNull()?.let { editor.putString(KEY_SELECTED_DEVICE_ID, it.id) }
        editor.apply()
    }

    companion object {
        private const val KEY_DEVICES                 = "devices"
        private const val KEY_SELECTED_DEVICE_ID       = "selected_device_id"
        private const val KEY_PAIRED_DEVICE_LEGACY     = "paired_device"
        private const val KEY_WEBHOOK_CONFIG_LEGACY    = "webhook_config"
        private const val KEY_DEMO                    = "demo_mode"
        private const val KEY_LAST_OPENED             = "last_opened_at"
        private const val KEY_LAST_AUTO_FIRED         = "last_auto_fired_at"
        private const val KEY_LAST_AUTO_FAILED        = "last_auto_failed_at"
        private const val KEY_LAST_SENDING            = "last_sending_at"
        private const val KEY_OUTSIDE_OUTER_GEOFENCE  = "outside_outer_geofence"
    }
}
