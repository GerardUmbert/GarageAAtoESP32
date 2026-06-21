package com.dunnowsoftware.GarageAAtoESP32.data

import org.json.JSONObject

enum class TriggerSource {
    MANUAL_PHONE,
    MANUAL_AA,
    AUTO_GEOFENCE,
}

enum class OpenOutcome {
    SUCCESS,
    FAILED_BLE,
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
) {
    fun toJson(): String = JSONObject().apply {
        put("ts", timestampMs)
        put("addr", deviceAddress)
        put("name", deviceName)
        put("trigger", trigger.name)
        put("outcome", outcome.name)
        if (detail != null) put("detail", detail)
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
            )
        } catch (_: Throwable) {
            null
        }
    }
}
