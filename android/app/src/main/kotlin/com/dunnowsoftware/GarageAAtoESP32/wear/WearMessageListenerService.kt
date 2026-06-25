package com.dunnowsoftware.GarageAAtoESP32.wear

import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.ble.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceForegroundService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.tasks.Tasks

private const val PATH_OPEN   = "/garage/open"
private const val PATH_RESULT = "/garage/result"
const val PATH_AUTOFIRED      = "/garage/autofired"

private const val RESULT_SUCCESS = "SUCCESS"
private const val RESULT_FAIL    = "FAIL"
private const val RESULT_NO_DEVICE = "NO_DEVICE"

class WearMessageListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_OPEN) return

        val prefs = DevicePreferences(this)
        val device = prefs.pairedDevice
        if (device == null) {
            sendResult(event.sourceNodeId, RESULT_NO_DEVICE)
            return
        }

        val bleManager = GarageBleManager(this)
        bleManager.connectAndOpen(
            deviceAddress = device.address,
            userPin = device.password,
            trigger = TriggerSource.WEAR,
            onAttempt = {},
        ) { result ->
            when (result) {
                is OpenResult.Success -> {
                    val ts = System.currentTimeMillis()
                    prefs.lastAutoFiredAt = ts
                    OpenHistoryStore.append(
                        this,
                        OpenHistoryEntry(
                            timestampMs   = ts,
                            deviceAddress = device.address,
                            deviceName    = device.name,
                            trigger       = TriggerSource.WEAR,
                            outcome       = OpenOutcome.SUCCESS,
                            detail        = null,
                        ),
                    )
                    LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(Intent(GeofenceForegroundService.ACTION_AUTO_OPENED))
                    sendResult(event.sourceNodeId, RESULT_SUCCESS)
                    bleManager.cleanup()
                }
                is OpenResult.Failure -> {
                    OpenHistoryStore.append(
                        this,
                        OpenHistoryEntry(
                            timestampMs   = System.currentTimeMillis(),
                            deviceAddress = device.address,
                            deviceName    = device.name,
                            trigger       = TriggerSource.WEAR,
                            outcome       = OpenOutcome.FAILED_BLE,
                            detail        = result.reason,
                        ),
                    )
                    sendResult(event.sourceNodeId, RESULT_FAIL)
                    bleManager.cleanup()
                }
            }
        }
    }

    private fun sendResult(nodeId: String, result: String) {
        try {
            Tasks.await(
                Wearable.getMessageClient(this)
                    .sendMessage(nodeId, PATH_RESULT, result.toByteArray()),
            )
        } catch (_: Exception) {}
    }
}
