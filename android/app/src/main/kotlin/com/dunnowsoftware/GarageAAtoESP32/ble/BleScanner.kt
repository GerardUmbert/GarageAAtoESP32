package com.dunnowsoftware.GarageAAtoESP32.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid

data class FoundDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

class BleScanner(context: Context) {

    private val scanner =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter.bluetoothLeScanner

    private var callback: ScanCallback? = null

    fun start(onResult: (FoundDevice) -> Unit) {
        stop()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: "Unknown"
                onResult(FoundDevice(name, result.device.address, result.rssi))
            }
        }
        callback = cb

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, cb)
    }

    /**
     * Background presence scan: low-power, filtered to a single MAC.
     * Used by the main screen to tell whether the paired opener is in range
     * right now without spending battery on full-throttle scanning.
     *
     * CALLBACK_TYPE_ALL_MATCHES disables Android's duplicate-filtering so we
     * get a callback for every advertisement, not just the first one until
     * the device "ages out" of the BT stack's internal cache. Without it,
     * presence flickers in/out of range as our staleness check fires
     * between callbacks.
     */
    fun startPresence(deviceAddress: String, onSeen: (FoundDevice) -> Unit) {
        stop()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: "Unknown"
                onSeen(FoundDevice(name, result.device.address, result.rssi))
            }
        }
        callback = cb

        val filter = ScanFilter.Builder()
            .setDeviceAddress(deviceAddress)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        scanner.startScan(listOf(filter), settings, cb)
    }

    fun stop() {
        callback?.let { scanner.stopScan(it) }
        callback = null
    }
}
