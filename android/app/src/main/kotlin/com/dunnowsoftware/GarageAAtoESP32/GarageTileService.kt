package com.dunnowsoftware.GarageAAtoESP32

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.ble.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchResult

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
        bleManager.connectAndOpen(paired.address, paired.password) { result ->
            mainLooper.let { android.os.Handler(it).post {
                when (result) {
                    is OpenResult.Success -> onOpenSuccess()
                    is OpenResult.Failure -> onOpenFailure()
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
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            subtitle = getString(R.string.tile_failed)
            updateTile()
        }
        Handler(Looper.getMainLooper()).postDelayed({ updateTile() }, 2000)
    }
}
