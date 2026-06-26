package com.dunnowsoftware.GarageAAtoESP32

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.ble.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchResult
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchSending

class GarageTileService : TileService() {

    private val bleManager by lazy { GarageBleManager(this) }

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

        val paired = prefs.pairedDevice ?: return
        setTileBusy()
        notifyWatchSending(this)
        bleManager.connectAndOpen(paired.address, paired.password,
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
                                deviceAddress = paired.address,
                                deviceName    = paired.name,
                                trigger       = TriggerSource.MANUAL_PHONE,
                                outcome       = OpenOutcome.SUCCESS,
                            ),
                        )
                        onOpenSuccess()
                    }
                    is OpenResult.Failure -> {
                        OpenHistoryStore.append(
                            this,
                            OpenHistoryEntry(
                                timestampMs   = System.currentTimeMillis(),
                                deviceAddress = paired.address,
                                deviceName    = paired.name,
                                trigger       = TriggerSource.MANUAL_PHONE,
                                outcome       = OpenOutcome.FAILED_BLE,
                                detail        = result.reason,
                            ),
                        )
                        onOpenFailure()
                    }
                }
            }}
        }
    }

    override fun onStopListening() {
        bleManager.cleanup()
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
