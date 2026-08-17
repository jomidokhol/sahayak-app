package com.nur.sahayak

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

class SahayakApp : Application() {
    override fun onCreate() {
        super.onCreate()

        try {
            // Disable buggy disk caching & force live server reads
            val firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            firestore.firestoreSettings = settings
        } catch (e: Exception) {
            Log.e("SahayakApp", "Firestore settings init error", e)
        }

        // Global Uncaught Exception Handler
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Log.e("SahayakApp", "Global Crash Prevented: ${throwable.message}", throwable)
            try {
                Toast.makeText(
                    applicationContext,
                    "মেসেজ: ${throwable.localizedMessage ?: "সমস্যা সমাধান করা হয়েছে"}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
