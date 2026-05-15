package com.dunnowsoftware.GarageAAtoESP32.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

fun vibrate(ctx: Context, pattern: LongArray) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val mgr = ctx.getSystemService(VibratorManager::class.java)
        mgr?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    } else {
        @Suppress("DEPRECATION")
        val vib = ctx.getSystemService(Vibrator::class.java)
        @Suppress("DEPRECATION")
        vib?.vibrate(pattern, -1)
    }
}

// Light click — used for all UI taps (buttons, rows, toggles, back)
val HAPTIC_TAP = longArrayOf(0, 18)
