package com.dunnowsoftware.GarageAAtoESP32

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TransportType
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceResolution
import com.dunnowsoftware.GarageAAtoESP32.geofence.resolveGeofenceTargets
import com.dunnowsoftware.GarageAAtoESP32.transport.MultiDeviceOpenCoordinator
import com.dunnowsoftware.GarageAAtoESP32.transport.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.ui.PhoneActivity
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchResult
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchSending

class GarageTileService : TileService() {

    private var coordinator: MultiDeviceOpenCoordinator? = null

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        val prefs = DevicePreferences(this)
        if (!prefs.isConfigured) {
            updateTile()
            return
        }

        if (prefs.demoMode) {
            setTileBusy()
            android.os.Handler(mainLooper).postDelayed({
                when (DemoOpener.nextResult()) {
                    is OpenResult.Success -> onOpenSuccess()
                    is OpenResult.Failure -> onOpenFailure()
                }
            }, DemoOpener.DELAY_MS)
            return
        }

        // With exactly one device paired there's nothing to resolve between —
        // fire it directly, same as before this phase existed. Geofence
        // resolution only comes into play once there's an actual choice to make.
        val targets: List<com.dunnowsoftware.GarageAAtoESP32.data.GarageDevice>
        if (prefs.devices.size <= 1) {
            targets = listOfNotNull(prefs.selectedDevice)
        } else {
            // Raw geofence presence, same rule as the phone dropdown's pre-selection —
            // no gates, since a tile tap is itself the human confirmation. Empty
            // resolution means "can't tell," so launch the phone's picker instead of
            // guessing (see PLAN_multiple_garages.md Phase 3, "Tile behavior").
            val resolution = resolveGeofenceTargets(prefs)
            val resolved = when (resolution) {
                is GeofenceResolution.Resolved -> resolution.devices
                GeofenceResolution.Empty -> emptyList()
            }
            if (resolved.isEmpty()) {
                launchPhonePicker()
                return
            }
            targets = resolved
        }
        if (targets.isEmpty()) {
            updateTile()
            return
        }

        setTileBusy()
        notifyWatchSending(this)
        val newCoordinator = MultiDeviceOpenCoordinator(this)
        coordinator = newCoordinator
        newCoordinator.open(
            devices = targets,
            trigger = TriggerSource.MANUAL_PHONE,
            onDeviceResult = { outcome ->
                val deviceOutcome = when {
                    outcome.result is OpenResult.Success -> OpenOutcome.SUCCESS
                    outcome.device.transport == TransportType.WEBHOOK -> OpenOutcome.FAILED_WEBHOOK
                    else -> OpenOutcome.FAILED_BLE
                }
                com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore.append(
                    this,
                    com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry(
                        timestampMs   = System.currentTimeMillis(),
                        deviceAddress = outcome.device.addressKey,
                        deviceName    = outcome.device.name,
                        trigger       = TriggerSource.MANUAL_PHONE,
                        outcome       = deviceOutcome,
                        detail        = (outcome.result as? OpenResult.Failure)?.reason,
                        deviceId      = outcome.device.id,
                        sessionId     = if (targets.size > 1) newCoordinator.sessionId else null,
                    ),
                )
            },
            onAllComplete = { outcomes ->
                Handler(mainLooper).post {
                    if (outcomes.any { it.result is OpenResult.Success }) onOpenSuccess() else onOpenFailure()
                }
            },
        )
    }

    private fun launchPhonePicker() {
        val intent = Intent(this, PhoneActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onStopListening() {
        coordinator?.cleanup()
    }

    private fun updateTile() {
        val configured = DevicePreferences(this).isConfigured
        qsTile?.apply {
            state = if (configured) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            subtitle = null
            updateTile()
        }
    }

    private fun setTileBusy() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            subtitle = getString(R.string.tile_sending)
            updateTile()
        }
    }

    private fun onOpenSuccess() {
        notifyWatchResult(this, true)
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            subtitle = getString(R.string.tile_opened)
            updateTile()
        }
        Handler(Looper.getMainLooper()).postDelayed({ updateTile() }, 2000)
    }

    private fun onOpenFailure() {
        notifyWatchResult(this, false)
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            subtitle = getString(R.string.tile_failed)
            updateTile()
        }
        Handler(Looper.getMainLooper()).postDelayed({ updateTile() }, 2000)
    }
}
