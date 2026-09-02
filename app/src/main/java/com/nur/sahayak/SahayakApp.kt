package com.nur.sahayak

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.nur.sahayak.workers.BloodNotificationWorker
import java.util.concurrent.TimeUnit

class SahayakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        setupEmergencyBloodBackgroundWorker()
    }

    private fun setupEmergencyBloodBackgroundWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val bloodWorkRequest = PeriodicWorkRequestBuilder<BloodNotificationWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "EmergencyBloodSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            bloodWorkRequest
        )
    }
}
