package com.dunnowsoftware.GarageAAtoESP32.wear

import android.content.Context
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import java.util.concurrent.Executors

private const val PATH_DEVICES = "/garage/devices"
private const val KEY_DEVICES_JSON = "devices_json"
private const val KEY_SELECTED_ID = "selected_id"
private const val KEY_RESOLVED_IDS_JSON = "resolved_ids_json"

enum class WatchTransportType { BLE, WEBHOOK }

data class WatchDevice(
    val id: String,
    val name: String,
    val transport: WatchTransportType,
)

data class WatchDeviceList(
    val devices: List<WatchDevice>,
    val selectedId: String?,
    // Device ids the phone's live geofence resolution currently resolves to
    // (raw presence, no driving gates — see PLAN_multiple_garages.md Phase 3).
    // Empty means "can't tell" — the picker/legacy single-device path applies.
    val resolvedIds: List<String> = emptyList(),
) {
    val resolvedDevices: List<WatchDevice>
        get() = resolvedIds.mapNotNull { id -> devices.firstOrNull { it.id == id } }
}

private val executor = Executors.newSingleThreadExecutor()

/** Reads the device list most recently pushed by the phone via the Data Layer. Empty if never synced. */
fun loadWatchDeviceList(context: Context, onResult: (WatchDeviceList) -> Unit) {
    executor.execute {
        try {
            val buffer = Tasks.await(Wearable.getDataClient(context).dataItems)
            val item = (0 until buffer.count)
                .map { buffer[it] }
                .firstOrNull { it.uri.path == PATH_DEVICES }
            val result = item?.let { parse(DataMapItem.fromDataItem(it.freeze())) } ?: WatchDeviceList(emptyList(), null)
            buffer.release()
            onResult(result)
        } catch (_: Exception) {
            onResult(WatchDeviceList(emptyList(), null))
        }
    }
}



fun parseWatchDeviceList(dataMapItem: DataMapItem): WatchDeviceList = parse(dataMapItem)

private fun parse(dataMapItem: DataMapItem): WatchDeviceList {
    val json = dataMapItem.dataMap.getString(KEY_DEVICES_JSON) ?: "[]"
    val selectedId = dataMapItem.dataMap.getString(KEY_SELECTED_ID)?.takeIf { it.isNotEmpty() }
    val resolvedIdsJson = dataMapItem.dataMap.getString(KEY_RESOLVED_IDS_JSON) ?: "[]"
    val arr = JSONArray(json)
    val devices = (0 until arr.length()).mapNotNull { i ->
        try {
            val o = arr.getJSONObject(i)
            WatchDevice(
                id = o.getString("id"),
                name = o.getString("name"),
                transport = WatchTransportType.valueOf(o.getString("transport")),
            )
        } catch (_: Exception) {
            null
        }
    }
    val resolvedArr = try { JSONArray(resolvedIdsJson) } catch (_: Exception) { JSONArray() }
    val resolvedIds = (0 until resolvedArr.length()).mapNotNull { i -> resolvedArr.optString(i).takeIf { it.isNotEmpty() } }
    return WatchDeviceList(devices, selectedId, resolvedIds)
}
