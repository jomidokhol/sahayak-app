package com.nur.sahayak.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nur.sahayak.BloodActivity
import com.nur.sahayak.R
import com.nur.sahayak.utils.FirestoreSafeParser
import kotlinx.coroutines.tasks.await

class BloodNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val channelId = "emergency_blood_channel"
    private val channelName = "জরুরি রক্তের নোটিফিকেশন"

    override suspend fun doWork(): Result {
        return try {
            val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            val lastNotifiedTime = sharedPref.getLong("last_notified_blood_post_time", System.currentTimeMillis())

            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("emergency_blood_posts")
                .orderBy("uploadtime", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .await()

            var latestTime = lastNotifiedTime

            for (doc in snapshot.documents) {
                val uploadTime = FirestoreSafeParser.parseTimestampToMillis(doc.get("uploadtime"))
                val expiry = FirestoreSafeParser.parseLong(doc.get("expiryTime"), 0L)
                val now = System.currentTimeMillis()

                if (uploadTime > lastNotifiedTime && (expiry == 0L || expiry > now)) {
                    val bloodGroup = doc.getString("bloodGroup") ?: "রক্ত"
                    val bloodAmount = doc.getString("bloodAmount") ?: "১ ব্যাগ"
                    val hospitalName = doc.getString("hospitalName") ?: "হাসপাতাল"
                    val postId = doc.id

                    sendBloodNotification(postId, bloodGroup, bloodAmount, hospitalName)

                    if (uploadTime > latestTime) {
                        latestTime = uploadTime
                    }
                }
            }

            sharedPref.edit().putLong("last_notified_blood_post_time", latestTime).apply()
            Result.success()
        } catch (e: Exception) {
            Log.e("BloodNotificationWorker", "Error checking new blood posts", e)
            Result.retry()
        }
    }

    private fun sendBloodNotification(postId: String, bloodGroup: String, bloodAmount: String, hospital: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "জরুরি রক্তের পোস্টের নোটিফিকেশন"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, BloodActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_post_id", postId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            postId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "🩸 জরুরি রক্তের প্রয়োজন!"
        val message = "জরুরি ভাবে $bloodAmount $bloodGroup রক্তের প্রয়োজন! স্থান $hospital। ট্যাপ করুন"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.blood)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setColor(Color.parseColor("#E53935"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(postId.hashCode(), notification)
    }
}
