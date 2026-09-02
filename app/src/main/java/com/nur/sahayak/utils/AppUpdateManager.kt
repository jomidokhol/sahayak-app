package com.nur.sahayak.utils

import android.app.Activity
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.nur.sahayak.R
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {

    private const val GITHUB_OWNER = "jomidokhol"
    private const val GITHUB_REPO = "sahayak-app"
    private const val CHANNEL_ID = "app_update_channel"

    data class ReleaseInfo(
        val versionName: String,
        val downloadUrl: String,
        val releaseNotes: String
    )

    fun getCurrentVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    fun checkForUpdate(
        context: Context,
        isManualCheck: Boolean = false,
        onResult: ((hasUpdate: Boolean, releaseInfo: ReleaseInfo?) -> Unit)? = null
    ) {
        Thread {
            try {
                val apiUrl = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "SahayakApp-Android")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tagName = json.getString("tag_name").replace("v", "").replace("V", "").trim()
                    val body = json.optString("body", "নতুন সংস্করণে বিভিন্ন বাগ ফিক্স ও গতি বৃদ্ধি করা হয়েছে।")
                    
                    var apkUrl = ""
                    val assets = json.getJSONArray("assets")
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    val currentVersion = getCurrentVersion(context).replace("v", "").replace("V", "").trim()
                    val hasUpdate = isNewerVersion(currentVersion, tagName) && apkUrl.isNotEmpty()
                    val releaseInfo = if (hasUpdate) ReleaseInfo(tagName, apkUrl, body) else null

                    Handler(Looper.getMainLooper()).post {
                        if (onResult != null) {
                            onResult(hasUpdate, releaseInfo)
                        } else if (hasUpdate && context is Activity && !context.isFinishing) {
                            showMandatoryUpdateDialog(context, releaseInfo!!)
                        } else if (isManualCheck) {
                            Toast.makeText(context, "আপনার অ্যাপটি ইতিমধ্যে সর্বশেষ সংস্করণে রয়েছে (v$currentVersion)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        if (isManualCheck) Toast.makeText(context, "গিটহাবে কোনো নতুন রিলিজ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                        onResult?.invoke(false, null)
                    }
                }
            } catch (e: Exception) {
                Log.e("AppUpdateManager", "Check Error", e)
                Handler(Looper.getMainLooper()).post {
                    if (isManualCheck) Toast.makeText(context, "ইন্টারনেট কানেকশন চেক করুন", Toast.LENGTH_SHORT).show()
                    onResult?.invoke(false, null)
                }
            }
        }.start()
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val currParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val lateParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val length = maxOf(currParts.size, lateParts.size)

            for (i in 0 until length) {
                val c = currParts.getOrElse(i) { 0 }
                val l = lateParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false
        } catch (e: Exception) {
            latest != current
        }
    }

    fun showMandatoryUpdateDialog(activity: Activity, info: ReleaseInfo) {
        val dialog = Dialog(activity, R.style.CustomDialogTheme).apply {
            setContentView(R.layout.dialog_in_app_update)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }

        val tvTitle = dialog.findViewById<TextView>(R.id.tvUpdateDialogTitle)
        val tvVersion = dialog.findViewById<TextView>(R.id.tvUpdateVersion)
        val tvNotes = dialog.findViewById<TextView>(R.id.tvUpdateNotes)
        val pbDownload = dialog.findViewById<ProgressBar>(R.id.pbUpdateDownload)
        val tvProgress = dialog.findViewById<TextView>(R.id.tvUpdateProgressPercent)
        val btnStart = dialog.findViewById<MaterialButton>(R.id.btnStartUpdate)

        tvTitle.text = "নতুন আপডেট পাওয়া গেছে!"
        tvVersion.text = "সংস্করণ: v${info.versionName}"
        tvNotes.text = info.releaseNotes

        btnStart.setOnClickListener {
            btnStart.isEnabled = false
            btnStart.text = "ডাউনলোড হচ্ছে..."
            pbDownload.visibility = View.VISIBLE
            tvProgress.visibility = View.VISIBLE

            downloadAndInstallApk(activity, info.downloadUrl,
                onProgress = { percent ->
                    pbDownload.progress = percent
                    tvProgress.text = "$percent%"
                },
                onComplete = { file ->
                    dialog.dismiss()
                    triggerInstall(activity, file)
                },
                onError = {
                    btnStart.isEnabled = true
                    btnStart.text = "পুনরায় চেষ্টা করুন"
                    pbDownload.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                    Toast.makeText(activity, "ডাউনলোড ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                }
            )
        }

        dialog.show()
    }

    private fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit,
        onComplete: (File) -> Unit,
        onError: () -> Unit
    ) {
        Thread {
            try {
                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connect()

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    Handler(Looper.getMainLooper()).post { onError() }
                    return@Thread
                }

                val fileLength = conn.contentLength
                val apkFile = File(context.cacheDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                val input: InputStream = conn.inputStream
                val output = FileOutputStream(apkFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        val percent = (total * 100 / fileLength).toInt()
                        Handler(Looper.getMainLooper()).post { onProgress(percent) }
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                sendInstallNotification(context, apkFile)

                Handler(Looper.getMainLooper()).post {
                    onComplete(apkFile)
                }

            } catch (e: Exception) {
                Log.e("AppUpdateManager", "Download Error", e)
                Handler(Looper.getMainLooper()).post { onError() }
            }
        }.start()
    }

    private fun triggerInstall(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "Install Error", e)
            Toast.makeText(context, "ইনস্টলার ওপেন করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendInstallNotification(context: Context, apkFile: File) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "App Update", NotificationManager.IMPORTANCE_HIGH)
                nm.createNotificationChannel(channel)
            }

            val apkUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                context, 0, installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.app_icon)
                .setContentTitle("সহায়ক অ্যাপ আপডেট প্রস্তুত")
                .setContentText("ইনস্টল সম্পন্ন করতে এখানে ট্যাপ করুন")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(1001, notif)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "Notification Error", e)
        }
    }
}
