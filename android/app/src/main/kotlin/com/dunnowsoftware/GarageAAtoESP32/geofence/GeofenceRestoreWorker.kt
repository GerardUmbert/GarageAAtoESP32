package com.dunnowsoftware.GarageAAtoESP32.geofence

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "geofence_restore"

class GeofenceRestoreWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        GeofenceManager(applicationContext).reregisterAll()
        return Result.success()
    }
}

fun scheduleGeofenceRestore(context: Context) {
    val request = PeriodicWorkRequestBuilder<GeofenceRestoreWorker>(15, TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}
