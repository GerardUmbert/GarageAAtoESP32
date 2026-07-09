package com.dunnowsoftware.GarageAAtoESP32

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dunnowsoftware.GarageAAtoESP32.transport.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.transport.OpenTransport
import com.dunnowsoftware.GarageAAtoESP32.transport.activeTransport
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TransportType
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchResult
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchSending

class GarageTileService : TileService() {

    private var currentTransport: OpenTransport? = null

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

        val selected = prefs.selectedDevice
        val transportType = selected?.transport
        val deviceAddress = selected?.addressKey ?: ""
        val deviceName = selected?.name ?: ""
        val transport = activeTransport(this, selected?.id) ?: return
        setTileBusy()
        notifyWatchSending(this)
        currentTransport = transport
        transport.open(
            trigger = TriggerSource.MANUAL_PHONE,
        ) { result ->
            mainLooper.let { android.os.Handler(it).post {
                when (result) {
                    is OpenResult.Success -> {
                        val ts = System.currentTimeMillis()
                        OpenHistoryStore.append(
                            this,
                            OpenHistoryEntry(
                                timestampMs   = ts,
                                deviceAddress = deviceAddress,
                                deviceName    = deviceName,
                                trigger       = TriggerSource.MANUAL_PHONE,
                                outcome       = OpenOutcome.SUCCESS,
                                deviceId      = selected?.id,
                            ),
                        )
                        onOpenSuccess()
                    }
                    is OpenResult.Failure -> {
                        val outcome = if (transportType == TransportType.WEBHOOK) OpenOutcome.FAILED_WEBHOOK else OpenOutcome.FAILED_BLE
                        OpenHistoryStore.append(
                            this,
                            OpenHistoryEntry(
                                timestampMs   = System.currentTimeMillis(),
                                deviceAddress = deviceAddress,
                                deviceName    = deviceName,
                                trigger       = TriggerSource.MANUAL_PHONE,
                                outcome       = outcome,
                                detail        = result.reason,
                                deviceId      = selected?.id,
                            ),
                        )
                        onOpenFailure()
                    }
                }
            }}
        }
    }

    override fun onStopListening() {
        currentTransport?.cleanup()
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
