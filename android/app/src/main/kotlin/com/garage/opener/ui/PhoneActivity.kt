package com.garage.opener.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.garage.opener.R
import com.garage.opener.data.DevicePreferences

class PhoneActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone)

        val prefs = DevicePreferences(this)
        val statusText = findViewById<TextView>(R.id.tv_status)
        val settingsBtn = findViewById<Button>(R.id.btn_settings)

        statusText.text = if (prefs.isConfigured)
            "Configured: ${prefs.deviceName ?: prefs.deviceAddress}\nUse the app in your car."
        else
            "Not configured yet.\nTap Settings to set up your garage."

        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh status after returning from SettingsActivity
        val prefs = DevicePreferences(this)
        val statusText = findViewById<TextView>(R.id.tv_status)
        statusText.text = if (prefs.isConfigured)
            "Configured: ${prefs.deviceName ?: prefs.deviceAddress}\nUse the app in your car."
        else
            "Not configured yet.\nTap Settings to set up your garage."
    }
}
