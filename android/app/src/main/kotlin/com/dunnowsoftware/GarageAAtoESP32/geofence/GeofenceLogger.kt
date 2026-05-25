package com.dunnowsoftware.GarageAAtoESP32.geofence

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LOG_FILE = "geofence.log"
private const val MAX_LINES = 500
private const val TAG = "GeofenceLogger"

object GeofenceLogger {

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(context: Context, level: String, tag: String, msg: String) {
        val line = "${fmt.format(Date())} $level/$tag: $msg"
        Log.println(
            when (level) {
                "E" -> Log.ERROR
                "W" -> Log.WARN
                "I" -> Log.INFO
                else -> Log.DEBUG
            },
            tag, msg
        )
        appendLine(context, line)
    }

    fun d(context: Context, tag: String, msg: String) = log(context, "D", tag, msg)
    fun i(context: Context, tag: String, msg: String) = log(context, "I", tag, msg)
    fun w(context: Context, tag: String, msg: String) = log(context, "W", tag, msg)
    fun e(context: Context, tag: String, msg: String) = log(context, "E", tag, msg)

    fun getLogFile(context: Context): File = File(context.filesDir, LOG_FILE)

    fun readLog(context: Context): String {
        val f = getLogFile(context)
        return if (f.exists()) f.readText() else "(log is empty)"
    }

    private fun appendLine(context: Context, line: String) {
        try {
            val file = getLogFile(context)
            val existing = if (file.exists()) file.readLines() else emptyList()
            val trimmed = if (existing.size >= MAX_LINES) existing.takeLast(MAX_LINES - 1) else existing
            file.writeText((trimmed + line).joinToString("\n") + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write geofence log: $e")
        }
    }
}
