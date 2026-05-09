package com.garage.opener.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.garage.opener.R
import com.garage.opener.ble.BleConstants
import com.garage.opener.data.DevicePreferences

class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy { DevicePreferences(this) }
    private val scanner by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter.bluetoothLeScanner
    }
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_TIMEOUT_MS = 15_000L

    private lateinit var pinEdit: EditText
    private lateinit var scanBtn: Button
    private lateinit var scanProgress: ProgressBar
    private lateinit var deviceList: ListView
    private val foundDevices = mutableListOf<Pair<String, String>>() // name, address
    private lateinit var adapter: ArrayAdapter<String>

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: "Unknown"
            val address = result.device.address
            if (foundDevices.none { it.second == address }) {
                foundDevices.add(name to address)
                adapter.add("$name  ($address)")
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        pinEdit = findViewById(R.id.et_pin)
        scanBtn = findViewById(R.id.btn_scan)
        scanProgress = findViewById(R.id.progress_scan)
        deviceList = findViewById(R.id.lv_devices)

        pinEdit.setText(prefs.pin)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        deviceList.adapter = adapter

        deviceList.setOnItemClickListener { _, _, position, _ ->
            val (name, address) = foundDevices[position]
            prefs.deviceAddress = address
            prefs.deviceName = name
            prefs.pin = pinEdit.text.toString().trim()
            Toast.makeText(this, "Saved: $name", Toast.LENGTH_SHORT).show()
            finish()
        }

        scanBtn.setOnClickListener { startScan() }

        findViewById<Button>(R.id.btn_save_pin).setOnClickListener {
            prefs.pin = pinEdit.text.toString().trim()
            Toast.makeText(this, "PIN saved", Toast.LENGTH_SHORT).show()
        }

        val demoCheckbox = findViewById<CheckBox>(R.id.cb_demo_mode)
        demoCheckbox.isChecked = prefs.demoMode
        demoCheckbox.setOnCheckedChangeListener { _, checked ->
            prefs.demoMode = checked
            val msg = if (checked) "Demo mode ON — tap Open Garage to simulate" else "Demo mode OFF"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startScan() {
        if (!hasBluetoothPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_BT
            )
            return
        }

        foundDevices.clear()
        adapter.clear()
        scanProgress.visibility = View.VISIBLE
        scanBtn.isEnabled = false

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)

        handler.postDelayed({
            scanner.stopScan(scanCallback)
            scanProgress.visibility = View.GONE
            scanBtn.isEnabled = true
            if (foundDevices.isEmpty()) {
                Toast.makeText(this, "No garage devices found", Toast.LENGTH_SHORT).show()
            }
        }, SCAN_TIMEOUT_MS)
    }

    private fun hasBluetoothPermissions(): Boolean {
        val perms = listOf(Manifest.permission.BLUETOOTH_SCAN,
                           Manifest.permission.BLUETOOTH_CONNECT,
                           Manifest.permission.ACCESS_FINE_LOCATION)
        return perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == REQUEST_BT && results.all { it == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (hasBluetoothPermissions()) scanner.stopScan(scanCallback)
    }

    companion object {
        private const val REQUEST_BT = 1001
    }
}
