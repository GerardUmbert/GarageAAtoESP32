package com.dunnowsoftware.GarageAAtoESP32.data

import android.content.Context
import java.io.File

private const val FILE_NAME = "open_history.jsonl"
private const val MAX_ENTRIES = 200

object OpenHistoryStore {

    private fun file(context: Context): File =
        File(context.filesDir, FILE_NAME)

    fun append(context: Context, entry: OpenHistoryEntry) {
        val f = file(context)
        // Prepend: read existing, keep newest MAX_ENTRIES-1, write new line first.
        val existing = readLines(f).take(MAX_ENTRIES - 1)
        f.writeText(buildString {
            appendLine(entry.toJson())
            existing.forEach { appendLine(it) }
        })
    }

    fun readAll(context: Context): List<OpenHistoryEntry> =
        readLines(file(context)).mapNotNull { OpenHistoryEntry.fromJson(it) }

    fun readByDevice(context: Context, deviceAddress: String): List<OpenHistoryEntry> =
        readAll(context).filter { it.deviceAddress == deviceAddress }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun readLines(f: File): List<String> =
        if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()
}
