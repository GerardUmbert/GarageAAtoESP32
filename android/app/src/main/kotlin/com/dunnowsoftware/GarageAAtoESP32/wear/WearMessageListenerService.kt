package com.dunnowsoftware.GarageAAtoESP32.wear

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
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceResolution
import com.dunnowsoftware.GarageAAtoESP32.geofence.resolveGeofenceTargets
import com.dunnowsoftware.GarageAAtoESP32.transport.MultiDeviceOpenCoordinator
import com.dunnowsoftware.GarageAAtoESP32.transport.OpenResult
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
        // string) — an explicit pick, honored exactly as before. An empty payload
        // means the watch's one-tap button was used (see WatchMainScreen /
        // WearActivity), so resolve live geofence state here, same rule as the
        // phone dropdown/AA/tile, falling back to selectedDevice if resolution is
        // empty (e.g. legacy single-device installs with no geofence at all).
        val requestedId = String(event.data).takeIf { it.isNotEmpty() }
        val targets = if (requestedId != null) {
            if (requestedId != prefs.selectedDeviceId && prefs.device(requestedId) != null) {
                prefs.selectedDeviceId = requestedId
                syncDevicesToWatch(this)
            }
            listOfNotNull(prefs.device(requestedId) ?: prefs.selectedDevice)
        } else {
            val resolved = (resolveGeofenceTargets(prefs) as? GeofenceResolution.Resolved)?.devices
            if (!resolved.isNullOrEmpty()) resolved else listOfNotNull(prefs.selectedDevice)
        }
        if (targets.isEmpty()) {
            inFlight = false
            notifyWatchResult(this, false)
            return
        }

        notifyWatchSending(this)
        mainHandler.post {
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_WEAR_SENDING))
        }

        val coordinator = MultiDeviceOpenCoordinator(this)
        coordinator.open(
            devices = targets,
            trigger = TriggerSource.WEAR,
            onDeviceResult = { outcome ->
                val deviceOutcome = when {
                    outcome.result is OpenResult.Success -> OpenOutcome.SUCCESS
                    outcome.device.transport == TransportType.WEBHOOK -> OpenOutcome.FAILED_WEBHOOK
                    else -> OpenOutcome.FAILED_BLE
                }
                OpenHistoryStore.append(
                    this,
                    OpenHistoryEntry(
                        timestampMs   = System.currentTimeMillis(),
                        deviceAddress = outcome.device.addressKey,
                        deviceName    = outcome.device.name,
                        trigger       = TriggerSource.WEAR,
                        outcome       = deviceOutcome,
                        detail        = (outcome.result as? OpenResult.Failure)?.reason,
                        deviceId      = outcome.device.id,
                        sessionId     = if (targets.size > 1) coordinator.sessionId else null,
                    ),
                )
            },
            onAllComplete = { outcomes ->
                val anySuccess = outcomes.any { it.result is OpenResult.Success }
                if (anySuccess) prefs.lastAutoFiredAt = System.currentTimeMillis()
                mainHandler.post {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(
                        Intent(if (anySuccess) GeofenceForegroundService.ACTION_AUTO_OPENED else GeofenceForegroundService.ACTION_AUTO_FAILED)
                    )
                }
                notifyWatchResult(this, anySuccess)
                coordinator.cleanup()
                inFlight = false
            },
        )
    }
}
