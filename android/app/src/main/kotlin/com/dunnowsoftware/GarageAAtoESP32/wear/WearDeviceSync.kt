package com.dunnowsoftware.GarageAAtoESP32.wear

import android.content.Context
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executors

private const val PATH_DEVICES = "/garage/devices"
private const val KEY_DEVICES_JSON = "devices_json"
private const val KEY_SELECTED_ID = "selected_id"
private const val KEY_RESOLVED_IDS_JSON = "resolved_ids_json"
private const val KEY_UPDATED_AT = "updated_at"

private val executor = Executors.newSingleThreadExecutor()

/**
 * Pushes the current device list + selection + geofence-resolved targets to
 * the watch via the Data Layer so the watch has a picker (and a one-tap
 * screen) to show without needing the phone reachable at the moment the
 * watch app is opened (DataClient persists/syncs, unlike a one-shot
 * MessageClient send). Call after any device list, selection, or
 * entered-geofence mutation (add/remove/rename/select, geofence ENTER/EXIT)
 * — phone-process call sites only, since this is the source of truth being
 * pushed.
 */
fun syncDevicesToWatch(context: Context) {
    executor.execute {
        try {
            val prefs = DevicePreferences(context)
            val json = org.json.JSONArray().apply {
                prefs.devices.forEach { d ->
                    put(org.json.JSONObject().apply {
                        put("id", d.id)
                        put("name", d.name)
                        put("transport", d.transport.name)
                    })
                }
            }
            // Only real geofence matches drive the watch's one-tap screen — the
            // no-geofence BLE fallback is deliberately excluded (see
            // GeofenceResolution.kt); those devices stay reachable via the
            // ordinary picker + the watch's own connect flow, same as today.
            // Meaningless (and skipped) with <=1 device — nothing to resolve
            // between, same single-device path as before this phase existed.
            val resolvedIdsJson = org.json.JSONArray().apply {
                if (prefs.devices.size > 1) {
                    prefs.geofenceMatchedTargets().forEach { put(it.id) }
                }
            }
            val request = PutDataMapRequest.create(PATH_DEVICES).apply {
                dataMap.putString(KEY_DEVICES_JSON, json.toString())
                dataMap.putString(KEY_SELECTED_ID, prefs.selectedDeviceId ?: "")
                dataMap.putString(KEY_RESOLVED_IDS_JSON, resolvedIdsJson.toString())
                dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            }
            Tasks.await(Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()))
        } catch (_: Exception) {}
    }
}
