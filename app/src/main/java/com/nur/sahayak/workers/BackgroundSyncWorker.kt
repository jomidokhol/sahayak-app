package com.nur.sahayak.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nur.sahayak.utils.AppUpdateManager
import com.nur.sahayak.utils.FirestoreSafeParser
import com.nur.sahayak.utils.NotificationHelper
import com.nur.sahayak.utils.OfflineContactManager
import kotlinx.coroutines.tasks.await

class BackgroundSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val prefs = appContext.getSharedPreferences("notification_sync_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        return try {
            if (!OfflineContactManager.isNetworkAvailable(appContext)) {
                return Result.success()
            }

            val firestore = FirebaseFirestore.getInstance()

            // 1. Check New News
            checkNewNews(firestore)

            // 2. Check User Post Reactions & Comments
            checkUserSocialUpdates(firestore)

            // 3. Check New Contacts Count
            checkNewContacts(firestore)

            // 4. Check App Update Releases
            checkAppUpdate()

            Result.success()
        } catch (e: Exception) {
            Log.e("BackgroundSyncWorker", "Sync Error", e)
            Result.success()
        }
    }

    private suspend fun checkNewNews(firestore: FirebaseFirestore) {
        try {
            val lastNewsTime = prefs.getLong("last_news_timestamp", System.currentTimeMillis())
            val snapshot = firestore.collection("news_list")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val doc = snapshot.documents[0]
                val timestamp = FirestoreSafeParser.parseTimestampToMillis(doc.get("timestamp"))

                if (timestamp > lastNewsTime) {
                    val newsId = doc.id
                    val title = FirestoreSafeParser.parseString(doc.get("title"))
                    val desc = FirestoreSafeParser.parseString(doc.get("desc"))
                    val imageUrl = FirestoreSafeParser.parseString(doc.get("imageUrl"))
                    val reporter = FirestoreSafeParser.parseString(doc.get("reporter"))
                    val viewCount = FirestoreSafeParser.parseInt(doc.get("viewCount"), 0)

                    NotificationHelper.showNewsNotification(
                        appContext, newsId, title, desc, imageUrl, reporter, viewCount, timestamp
                    )

                    prefs.edit().putLong("last_news_timestamp", timestamp).apply()
                }
            }
        } catch (e: Exception) {
            Log.e("BackgroundSyncWorker", "News check error", e)
        }
    }

    private suspend fun checkUserSocialUpdates(firestore: FirebaseFirestore) {
        try {
            val userPrefs = appContext.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            val currentUid = userPrefs.getString("user_uid", "") ?: ""

            if (currentUid.isNotEmpty()) {
                val snapshot = firestore.collection("openchat")
                    .whereEqualTo("userid", currentUid)
                    .get()
                    .await()

                var totalNewLikes = 0
                var totalNewReplies = 0

                for (doc in snapshot.documents) {
                    val postId = doc.id
                    val currentLikes = FirestoreSafeParser.parseInt(doc.get("likesCount"), 0)
                    val currentReplies = FirestoreSafeParser.parseInt(doc.get("repliesCount"), 0)

                    val cachedLikes = prefs.getInt("likes_$postId", currentLikes)
                    val cachedReplies = prefs.getInt("replies_$postId", currentReplies)

                    if (currentLikes > cachedLikes) {
                        totalNewLikes += (currentLikes - cachedLikes)
                        prefs.edit().putInt("likes_$postId", currentLikes).apply()
                    }

                    if (currentReplies > cachedReplies) {
                        totalNewReplies += (currentReplies - cachedReplies)
                        prefs.edit().putInt("replies_$postId", currentReplies).apply()
                    }
                }

                val userAvatar = userPrefs.getString("user_photo_url", "") ?: ""

                if (totalNewLikes > 0) {
                    NotificationHelper.showPostReactionNotification(appContext, totalNewLikes, userAvatar)
                }

                if (totalNewReplies > 0) {
                    NotificationHelper.showPostCommentNotification(appContext, totalNewReplies, userAvatar)
                }
            }
        } catch (e: Exception) {
            Log.e("BackgroundSyncWorker", "Social check error", e)
        }
    }

    private suspend fun checkNewContacts(firestore: FirebaseFirestore) {
        try {
            val snapshot = firestore.collection("contacts")
                .whereEqualTo("isApproved", true)
                .get()
                .await()

            val onlineCount = snapshot.size()
            val localCount = OfflineContactManager.getLastSyncCount(appContext)

            if (onlineCount > localCount && localCount > 0) {
                val diff = onlineCount - localCount
                val lastNotifiedCount = prefs.getInt("last_notified_contact_count", localCount)

                if (onlineCount > lastNotifiedCount) {
                    NotificationHelper.showNewContactsNotification(appContext, diff)
                    prefs.edit().putInt("last_notified_contact_count", onlineCount).apply()
                }
            }
        } catch (e: Exception) {
            Log.e("BackgroundSyncWorker", "Contacts check error", e)
        }
    }

    private fun checkAppUpdate() {
        AppUpdateManager.checkForUpdate(appContext, isManualCheck = false) { hasUpdate, releaseInfo ->
            if (hasUpdate && releaseInfo != null) {
                val lastNotifiedVersion = prefs.getString("last_notified_app_version", "") ?: ""
                if (releaseInfo.versionName != lastNotifiedVersion) {
                    NotificationHelper.showAppUpdateNotification(
                        appContext, releaseInfo.versionName, releaseInfo.releaseNotes
                    )
                    prefs.edit().putString("last_notified_app_version", releaseInfo.versionName).apply()
                }
            }
        }
    }
}
