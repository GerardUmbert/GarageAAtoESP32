package com.dunnowsoftware.GarageAAtoESP32.transport

import android.content.Context
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport-agnostic "open the garage" contract. Two implementations exist:
 * [BleTransport] (connect to a paired ESP32 over BLE) and [WebhookTransport]
 * (POST to a user-configured URL, e.g. a Home Assistant webhook). Callers
 * don't need to know which one is active — see [activeTransport].
 */
interface OpenTransport {
    val state: StateFlow<OpenResult?>

    fun open(
        trigger: TriggerSource,
        onAttempt: (attempt: Int) -> Unit = {},
        onResult: (OpenResult) -> Unit,
    )

    fun cleanup()
}

/**
 * Reads [DevicePreferences] and returns the [OpenTransport] for whichever
 * pairing is currently active (BLE device or webhook config — the two are
 * mutually exclusive). Returns null if nothing is configured.
 */
fun activeTransport(context: Context): OpenTransport? {
    val prefs = DevicePreferences(context)
    prefs.pairedDevice?.let { return BleTransport(context, it) }
    prefs.webhookConfig?.let { return WebhookTransport(context, it) }
    return null
}
