package com.dunnowsoftware.GarageAAtoESP32.wear

import com.dunnowsoftware.GarageAAtoESP32.transport.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.transport.activeTransport
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TransportType
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
        // A watch picker tap sends the chosen device's id as the payload (UTF-8
        // string); the legacy/no-picker case sends an empty payload and we fall
        // back to whatever's currently selected, same as AA/tile.
        val requestedId = String(event.data).takeIf { it.isNotEmpty() }
        if (requestedId != null && requestedId != prefs.selectedDeviceId && prefs.device(requestedId) != null) {
            prefs.selectedDeviceId = requestedId
            syncDevicesToWatch(this)
        }
        val selected = prefs.selectedDevice
        val transportType = selected?.transport
        val deviceAddress = selected?.addressKey ?: ""
        val deviceName = selected?.name ?: ""
        val transport = activeTransport(this, selected?.id)
        if (transport == null) {
            inFlight = false
            notifyWatchResult(this, false)
            return
        }

        notifyWatchSending(this)
        mainHandler.post {
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_WEAR_SENDING))
        }

        transport.open(
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
                            deviceAddress = deviceAddress,
                            deviceName    = deviceName,
                            trigger       = TriggerSource.WEAR,
                            outcome       = OpenOutcome.SUCCESS,
                            detail        = null,
                            deviceId      = selected?.id,
                        ),
                    )
                    mainHandler.post {
                        LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(Intent(GeofenceForegroundService.ACTION_AUTO_OPENED))
                    }
                    notifyWatchResult(this, true)
                    transport.cleanup()
                    inFlight = false
                }
                is OpenResult.Failure -> {
                    val outcome = if (transportType == TransportType.WEBHOOK) OpenOutcome.FAILED_WEBHOOK else OpenOutcome.FAILED_BLE
                    OpenHistoryStore.append(
                        this,
                        OpenHistoryEntry(
                            timestampMs   = System.currentTimeMillis(),
                            deviceAddress = deviceAddress,
                            deviceName    = deviceName,
                            trigger       = TriggerSource.WEAR,
                            outcome       = outcome,
                            detail        = result.reason,
                            deviceId      = selected?.id,
                        ),
                    )
                    mainHandler.post {
                        LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(Intent(GeofenceForegroundService.ACTION_AUTO_FAILED))
                    }
                    notifyWatchResult(this, false)
                    transport.cleanup()
                    inFlight = false
                }
            }
        }
    }
}
