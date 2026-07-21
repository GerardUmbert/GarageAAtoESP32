package com.dunnowsoftware.GarageAAtoESP32.transport

import android.content.Context
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.GarageDevice
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import java.util.UUID

/** Outcome of firing one device as part of a (possibly multi-device) open action. */
data class DeviceOpenOutcome(
    val device: GarageDevice,
    val result: OpenResult,
)

/**
 * Fires every device in [devices] independently and reports each one's
 * result as it completes, plus a final aggregate callback once all are
 * done. There is no atomicity across devices — one failing does not cancel
 * or roll back the others, since there's no way to "undo" a real garage
 * door opening and no reason a detached garage timing out should block an
 * attached one that succeeded. Devices are fired in parallel: no ordering
 * guarantee on completion.
 *
 * [sessionId] is a shared identifier callers can use for the resulting
 * OpenHistoryEntry.sessionId when [devices].size > 1, so history can group
 * them. Left null (and omitted from history) for a single-device fire —
 * grouping a single entry has nothing to group with.
 */
class MultiDeviceOpenCoordinator(private val context: Context) {

    private val activeTransports = mutableListOf<OpenTransport>()

    /** Set by [open] before any device fires — read this from [onDeviceResult] callbacks, not the return value, since it must be available to the very first callback. */
    var sessionId: String? = null
        private set

    /**
     * @param onDeviceAttempt invoked as each device reports a retry attempt (BLE/webhook transports retry internally).
     * @param onDeviceResult invoked once per device as its own attempt finishes.
     * @param onAllComplete invoked once, after every device has reported a result.
     */
    fun open(
        devices: List<GarageDevice>,
        trigger: TriggerSource,
        onDeviceAttempt: (device: GarageDevice, attempt: Int) -> Unit = { _, _ -> },
        onDeviceResult: (DeviceOpenOutcome) -> Unit,
        onAllComplete: (List<DeviceOpenOutcome>) -> Unit,
    ): String? {
        val sessionId = if (devices.size > 1) UUID.randomUUID().toString() else null
        this.sessionId = sessionId
        if (devices.isEmpty()) {
            onAllComplete(emptyList())
            return sessionId
        }

        val results = mutableListOf<DeviceOpenOutcome>()
        var remaining = devices.size

        devices.forEach { device ->
            val transport = activeTransport(context, device.id)
            if (transport == null) {
                val outcome = DeviceOpenOutcome(device, OpenResult.Failure("No active transport"))
                synchronized(results) {
                    results += outcome
                    remaining -= 1
                    onDeviceResult(outcome)
                    if (remaining == 0) onAllComplete(results.toList())
                }
                return@forEach
            }
            synchronized(activeTransports) { activeTransports += transport }
            transport.open(
                trigger = trigger,
                onAttempt = { n -> onDeviceAttempt(device, n) },
            ) { result ->
                val outcome = DeviceOpenOutcome(device, result)
                synchronized(results) {
                    results += outcome
                    remaining -= 1
                    onDeviceResult(outcome)
                    if (remaining == 0) onAllComplete(results.toList())
                }
            }
        }
        return sessionId
    }

    fun cleanup() {
        synchronized(activeTransports) {
            activeTransports.forEach { it.cleanup() }
            activeTransports.clear()
        }
    }
}
