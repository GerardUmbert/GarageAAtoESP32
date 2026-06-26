package com.dunnowsoftware.GarageAAtoESP32.wear

import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.ble.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceForegroundService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

private val mainHandler = Handler(Looper.getMainLooper())

private const val PATH_OPEN = "/garage/open"

class WearMessageListenerService : WearableListenerService() {

    companion object {
        const val ACTION_WEAR_SENDING = "com.dunnowsoftware.GarageAAtoESP32.ACTION_WEAR_SENDING"
        @Volatile private var inFlight = false
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_OPEN) return
        if (inFlight) return
        inFlight = true

        val prefs = DevicePreferences(this)
        val device = prefs.pairedDevice
        if (device == null) {
            inFlight = false
            notifyWatchResult(this, false)
            return
        }

        notifyWatchSending(this)
        mainHandler.post {
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_WEAR_SENDING))
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
                    mainHandler.post {
                        LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(Intent(GeofenceForegroundService.ACTION_AUTO_OPENED))
                    }
                    notifyWatchResult(this, true)
                    bleManager.cleanup()
                    inFlight = false
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
                    mainHandler.post {
                        LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(Intent(GeofenceForegroundService.ACTION_AUTO_FAILED))
                    }
                    notifyWatchResult(this, false)
                    bleManager.cleanup()
                    inFlight = false
                }
            }
        }
    }
}
