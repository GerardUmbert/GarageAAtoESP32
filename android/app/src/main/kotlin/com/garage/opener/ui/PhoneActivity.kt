package com.garage.opener.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.garage.opener.R
import com.garage.opener.ble.GarageBleManager
import com.garage.opener.ble.OpenResult
import com.garage.opener.data.DevicePreferences

class PhoneActivity : AppCompatActivity() {

    private lateinit var prefs: DevicePreferences
    private lateinit var statusText: TextView
    private lateinit var openBtn: Button
    private val bleManager by lazy { GarageBleManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone)

        prefs = DevicePreferences(this)
        statusText = findViewById(R.id.tv_status)
        openBtn = findViewById(R.id.btn_open)

        openBtn.setOnClickListener { triggerOpen() }

        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.cleanup()
    }

    private fun refreshUi() {
        when {
            prefs.demoMode -> {
                statusText.text = "Demo mode on"
                openBtn.isEnabled = true
            }
            prefs.isConfigured -> {
                statusText.text = "Garage: ${prefs.deviceName ?: prefs.deviceAddress}"
                openBtn.isEnabled = true
            }
            else -> {
                statusText.text = "Not configured yet.\nTap Settings to set up your garage."
                openBtn.isEnabled = false
            }
        }
    }

    private fun triggerOpen() {
        if (prefs.demoMode) {
            openBtn.isEnabled = false
            openBtn.text = "Connecting…"
            Toast.makeText(this, "DEMO: simulating garage open…", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                Toast.makeText(this, "DEMO: relay would trigger now", Toast.LENGTH_LONG).show()
                openBtn.text = "Open Garage"
                openBtn.isEnabled = true
            }, 1500)
            return
        }

        val address = prefs.deviceAddress ?: return
        openBtn.isEnabled = false
        openBtn.text = "Connecting…"

        bleManager.connectAndOpen(address, prefs.pin) { result ->
            runOnUiThread {
                openBtn.text = "Open Garage"
                openBtn.isEnabled = true
                when (result) {
                    is OpenResult.Success ->
                        Toast.makeText(this, "Opened!", Toast.LENGTH_SHORT).show()
                    is OpenResult.Failure ->
                        Toast.makeText(this, "Failed: ${result.reason}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
