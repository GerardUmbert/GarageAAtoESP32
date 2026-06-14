package com.dunnowsoftware.GarageAAtoESP32.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

private const val PREFS_NAME = "activity_recognition"
private const val KEY_TYPE = "last_activity_type"
private const val KEY_CONFIDENCE = "last_activity_confidence"
private const val KEY_TIME = "last_activity_time"

class ActivityUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val activity = result.mostProbableActivity
        prefs(context).edit()
            .putInt(KEY_TYPE, activity.type)
            .putInt(KEY_CONFIDENCE, activity.confidence)
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    companion object {
        fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun lastActivityType(context: Context): Int =
            prefs(context).getInt(KEY_TYPE, DetectedActivity.UNKNOWN)

        fun lastActivityConfidence(context: Context): Int =
            prefs(context).getInt(KEY_CONFIDENCE, 0)

        fun lastActivityAgeMs(context: Context): Long {
            val t = prefs(context).getLong(KEY_TIME, 0L)
            return if (t == 0L) -1L else System.currentTimeMillis() - t
        }
    }
}
