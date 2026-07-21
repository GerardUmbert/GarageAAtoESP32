package com.dunnowsoftware.GarageAAtoESP32.data

import org.json.JSONObject

enum class TriggerSource {
    MANUAL_PHONE,
    MANUAL_AA,
    AUTO_GEOFENCE,
    VOICE,
    WEAR,
}

enum class OpenOutcome {
    SUCCESS,
    FAILED_BLE,
    FAILED_WEBHOOK,
    SUPPRESSED,
}

data class OpenHistoryEntry(
    val timestampMs: Long,
    val deviceAddress: String,
    val deviceName: String,
    val trigger: TriggerSource,
    val outcome: OpenOutcome,
    // For AUTO_GEOFENCE SUCCESS/FAILED_BLE: which gate passed (e.g. "IN_VEHICLE", "SPEED", "AA_CONNECTED")
    // For SUPPRESSED: reason (e.g. "ON_FOOT", "SPEED_TOO_LOW", "AA_NOT_CONNECTED")
    val detail: String? = null,
    // GarageDevice.id this entry is for. Null for entries written before the
    // multiple-garages migration — deviceAddress/deviceName remain the
    // display fallback for those.
    val deviceId: String? = null,
    // Shared by every entry resulting from one trigger action that fired
    // 2+ devices at once (geofence resolution matching multiple devices,
    // or the no-geofence BLE fallback) — lets the history UI group them.
    // Null for single-device fires, same as today.
    val sessionId: String? = null,
) {
    fun toJson(): String = JSONObject().apply {
        put("ts", timestampMs)
        put("addr", deviceAddress)
        put("name", deviceName)
        put("trigger", trigger.name)
        put("outcome", outcome.name)
        if (detail != null) put("detail", detail)
        if (deviceId != null) put("device_id", deviceId)
        if (sessionId != null) put("session_id", sessionId)
    }.toString()

    companion object {
        fun fromJson(raw: String): OpenHistoryEntry? = try {
            val o = JSONObject(raw)
            OpenHistoryEntry(
                timestampMs   = o.getLong("ts"),
                deviceAddress = o.getString("addr"),
                deviceName    = o.getString("name"),
                trigger       = TriggerSource.valueOf(o.getString("trigger")),
                outcome       = OpenOutcome.valueOf(o.getString("outcome")),
                detail        = o.optString("detail").takeIf { it.isNotEmpty() },
                deviceId      = o.optString("device_id").takeIf { it.isNotEmpty() },
                sessionId     = o.optString("session_id").takeIf { it.isNotEmpty() },
            )
        } catch (_: Throwable) {
            null
        }
    }
}
