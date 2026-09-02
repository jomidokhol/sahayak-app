package com.nur.sahayak.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.nur.sahayak.MainActivity
import com.nur.sahayak.NewsDetailActivity
import com.nur.sahayak.R

object NotificationHelper {

    private const val CHANNEL_NEWS = "channel_news_updates"
    private const val CHANNEL_SOCIAL = "channel_social_reactions"
    private const val CHANNEL_CONTACTS = "channel_contacts_updates"
    private const val CHANNEL_APP_UPDATES = "channel_app_releases"

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val newsChannel = NotificationChannel(
                CHANNEL_NEWS, "সংবাদ ও জরুরি বিজ্ঞপ্তি", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "নতুন সংবাদ ও খবরের আপডেট" }

            val socialChannel = NotificationChannel(
                CHANNEL_SOCIAL, "পোস্ট রিঅ্যাকশন ও কমেন্ট", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "ওপেন চ্যাট পোস্টে লাইক এবং কমেন্ট নোটিফিকেশন" }

            val contactsChannel = NotificationChannel(
                CHANNEL_CONTACTS, "কন্টাক্ট ডিরেক্টরি আপডেট", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "নতুন কন্টাক্ট সংযোজন তথ্য" }

            val appUpdatesChannel = NotificationChannel(
                CHANNEL_APP_UPDATES, "অ্যাপ ভার্সন আপডেট", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "নতুন অ্যাপ সংস্করণ রিলিজ নোটিফিকেশন" }

            nm.createNotificationChannels(listOf(newsChannel, socialChannel, contactsChannel, appUpdatesChannel))
        }
    }

    fun showNewsNotification(
        context: Context,
        newsId: String,
        title: String,
        desc: String,
        imageUrl: String,
        reporter: String,
        viewCount: Int,
        timestamp: Long
    ) {
        initChannels(context)

        val intent = Intent(context, NewsDetailActivity::class.java).apply {
            putExtra("id", newsId)
            putExtra("title", title)
            putExtra("reporter", reporter)
            putExtra("imageUrl", imageUrl)
            putExtra("desc", desc)
            putExtra("viewCount", viewCount)
            putExtra("timestamp", timestamp)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context, newsId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        fun dispatch(bitmap: Bitmap?) {
            val builder = NotificationCompat.Builder(context, CHANNEL_NEWS)
                .setSmallIcon(R.drawable.app_icon)
                .setContentTitle("📰 $title")
                .setContentText(desc.take(80) + "...")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)

            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setSummaryText(title)
                )
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(newsId.hashCode(), builder.build())
        }

        if (imageUrl.isNotEmpty()) {
            Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        dispatch(resource)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        dispatch(null)
                    }
                })
        } else {
            dispatch(null)
        }
    }

    fun showPostReactionNotification(context: Context, reactionCount: Int, userAvatar: String) {
        initChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_tab", "PROFILE")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 2001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val message = "Your post has $reactionCount reactions. Tap to view"

        val builder = NotificationCompat.Builder(context, CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("❤️ পোস্ট রিঅ্যাকশন")
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2001, builder.build())
    }

    fun showPostCommentNotification(context: Context, commentCount: Int, userAvatar: String) {
        initChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_tab", "PROFILE")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 2002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val message = "Your post have $commentCount new comments. Tap to view"

        val builder = NotificationCompat.Builder(context, CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("💬 নতুন মন্তব্য")
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2002, builder.build())
    }

    fun showNewContactsNotification(context: Context, newContactsCount: Int) {
        initChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_tab", "SERVICES")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 3001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = "📞 নতুন জরুরি কন্টাক্ট সংযোজন"
        val message = "লালপুরে $newContactsCount টি নতুন জরুরি কন্টাক্ট যুক্ত হয়েছে! অফলাইনে পেতে সেটিংসে ডাউনলোড করুন।"

        val builder = NotificationCompat.Builder(context, CHANNEL_CONTACTS)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(3001, builder.build())
    }

    fun showAppUpdateNotification(context: Context, version: String, releaseNotes: String) {
        initChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_tab", "PROFILE_SETTINGS")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 4001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = "🚀 সহায়ক অ্যাপের নতুন আপডেট (v$version)"
        val message = "নতুন সংস্করণ v$version পাওয়া গেছে! ইনস্টল করতে ট্যাপ করুন।"

        val builder = NotificationCompat.Builder(context, CHANNEL_APP_UPDATES)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message\n\nবিবরণ: $releaseNotes"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(4001, builder.build())
    }
}
