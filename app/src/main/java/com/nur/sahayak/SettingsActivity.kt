package com.nur.sahayak

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.models.ActiveDonor
import com.nur.sahayak.models.EmergencyBloodPost
import com.nur.sahayak.utils.FirestoreSafeParser
import com.nur.sahayak.utils.TopNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SettingsActivity : AppCompatActivity() {

    private val currentAppVersion = "1.8"

    private lateinit var btnBack: ImageButton
    private lateinit var btnDownloadAll: Button
    private lateinit var switchAuto: SwitchMaterial
    private lateinit var btnCheckUpdate: Button
    private lateinit var tvVersionDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        btnBack = findViewById(R.id.btnBackSettings)
        btnDownloadAll = findViewById(R.id.btnDownloadAllJson)
        switchAuto = findViewById(R.id.switchAutoDownload)
        btnCheckUpdate = findViewById(R.id.btnCheckAppUpdate)
        tvVersionDisplay = findViewById(R.id.tvCurrentVersionDisplay)

        btnBack.setOnClickListener { finish() }

        tvVersionDisplay.text = "v$currentAppVersion"

        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isAutoDownloadEnabled = sharedPref.getBoolean("auto_download_enabled", true)
        switchAuto.isChecked = isAutoDownloadEnabled

        switchAuto.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("auto_download_enabled", isChecked).apply()
            val msg = if (isChecked) "অটো ডেটা ডাউনলোড ও সিঙ্ক চালু করা হয়েছে" else "অটো ডেটা ডাউনলোড বন্ধ করা হয়েছে"
            TopNotification.show(this, msg)
        }

        btnDownloadAll.setOnClickListener {
            downloadAllDataToJson()
        }

        // Live In-App Update Checker
        btnCheckUpdate.setOnClickListener {
            checkGitHubForUpdates()
        }
    }

    private fun checkGitHubForUpdates() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("নতুন আপডেট চেক করা হচ্ছে...")
            setCancelable(false)
            show()
        }

        Thread {
            try {
                val url = URL("https://api.github.com/repos/nurmohammad25/sahayak-app/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val json = JSONObject(reader.readText())
                    reader.close()

                    val remoteVersion = json.optString("tag_name", "").removePrefix("v").trim()
                    val releaseNotes = json.optString("body", "নতুন ফিচার ও বাগ ফিক্স করা হয়েছে।")
                    val htmlUrl = json.optString("html_url", "https://github.com/nurmohammad25/sahayak-app/releases")

                    var downloadUrl = htmlUrl
                    val assets = json.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        val firstAsset = assets.getJSONObject(0)
                        val apkDownload = firstAsset.optString("browser_download_url", "")
                        if (apkDownload.isNotEmpty()) {
                            downloadUrl = apkDownload
                        }
                    }

                    Handler(Looper.getMainLooper()).post {
                        progressDialog.dismiss()
                        if (isNewerVersion(remoteVersion, currentAppVersion)) {
                            showUpdateAvailableDialog(remoteVersion, releaseNotes, downloadUrl)
                        } else {
                            showAlreadyLatestDialog()
                        }
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        progressDialog.dismiss()
                        showAlreadyLatestDialog()
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    progressDialog.dismiss()
                    Toast.makeText(this, "আপডেট চেক ব্যর্থ: ইন্টারনেট সংযোগ পরীক্ষা করুন", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun isNewerVersion(remoteVer: String, currentVer: String): Boolean {
        try {
            val remoteParts = remoteVer.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVer.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLength = maxOf(remoteParts.size, currentParts.size)

            for (i in 0 until maxLength) {
                val r = if (i < remoteParts.size) remoteParts[i] else 0
                val c = if (i < currentParts.size) currentParts[i] else 0
                if (r > c) return true
                if (r < c) return false
            }
            return false
        } catch (e: Exception) {
            return remoteVer != currentVer
        }
    }

    private fun showUpdateAvailableDialog(newVer: String, notes: String, downloadUrl: String) {
        MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle("🚀 নতুন আপডেট উপলব্ধ (v$newVer)")
            .setMessage("সহায়ক অ্যাপের নতুন সংস্করণ প্রকাশিত হয়েছে।\n\n📝 চেঞ্জলগ:\n$notes\n\nউন্নত পারফরম্যান্স ও নতুন সুবিধা পেতে এখনই আপডেট করুন।")
            .setPositiveButton("ডাউনলোড ও আপডেট") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                } catch (e: Exception) {
                    Toast.makeText(this, "ব্রাউজার ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("পরে", null)
            .show()
    }

    private fun showAlreadyLatestDialog() {
        MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle("✨ অ্যাপ আপ-টু-ডেট আছে")
            .setMessage("আপনি সহায়ক অ্যাপের সর্বশেষ সংস্করণ ব্যবহার করছেন।\n\nবর্তমান ভার্সন: v$currentAppVersion")
            .setPositiveButton("ঠিক আছে", null)
            .show()
    }

    private fun downloadAllDataToJson() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("কন্টাক্ট, রক্তদাতা ও রক্তের পোস্ট ডাউনলোড হচ্ছে...")
            setCancelable(false)
            show()
        }

        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("contacts").whereEqualTo("isApproved", true).get()
            .addOnSuccessListener { contactsSnapshot ->
                saveContactsJson(contactsSnapshot.documents)

                firestore.collection("users").whereEqualTo("isDonorRegistered", true).get()
                    .addOnSuccessListener { donorsSnapshot ->
                        saveDonorsJson(donorsSnapshot.documents)

                        firestore.collection("emergency_blood_posts").whereEqualTo("status", "active").get()
                            .addOnSuccessListener { bloodSnapshot ->
                                saveEmergencyBloodJson(bloodSnapshot.documents)

                                progressDialog.dismiss()
                                TopNotification.show(this, "কন্টাক্ট, রক্তদাতা ও রক্তের পোস্ট সফলভাবে ডাউনলোড হয়েছে!")
                                Toast.makeText(this, "সকল অফলাইন ডেটা sahayak ফোল্ডারে সংরক্ষিত হয়েছে", Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener {
                                progressDialog.dismiss()
                                TopNotification.show(this, "কন্টাক্ট ও রক্তদাতা সেভ হয়েছে, রক্তের পোস্ট ব্যর্থ")
                            }
                    }
                    .addOnFailureListener {
                        progressDialog.dismiss()
                        Toast.makeText(this, "ডোনার ডেটা ডাউনলোড ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                Toast.makeText(this, "কন্টাক্ট ডাউনলোড ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveContactsJson(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        try {
            val jsonArray = JSONArray()
            for (doc in docs) {
                val obj = JSONObject().apply {
                    put("id", doc.id)
                    put("name", doc.getString("name") ?: "")
                    put("category", doc.getString("category") ?: "")
                    put("phone", doc.getString("phone") ?: "")
                    put("title", doc.getString("title") ?: "")
                    put("location", doc.getString("location") ?: "")
                    put("whatsapp", doc.getString("whatsapp") ?: "")
                    put("facebook", doc.getString("facebook") ?: "")
                }
                jsonArray.put(obj)
            }

            val internalFile = File(filesDir, "contacts.json")
            internalFile.writeText(jsonArray.toString())

            val sahayakDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "sahayak")
            if (!sahayakDir.exists()) sahayakDir.mkdirs()
            val publicFile = File(sahayakDir, "contacts.json")
            publicFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error saving contacts.json", e)
        }
    }

    private fun saveDonorsJson(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        try {
            val jsonArray = JSONArray()
            for (doc in docs) {
                val bloodGroup = doc.getString("bloodGroup") ?: ""
                val isVisible = doc.getBoolean("isDonorVisible") ?: true
                val lastDonation = doc.getLong("lastDonationDateTimestamp") ?: 0L

                if (bloodGroup.isNotEmpty() && isVisible) {
                    val fName = doc.getString("firstName") ?: ""
                    val lName = doc.getString("lastName") ?: ""
                    val fullName = if ("$fName $lName".trim().isNotEmpty()) "$fName $lName".trim() else (doc.getString("name") ?: "রক্তদাতা")

                    val obj = JSONObject().apply {
                        put("uid", doc.id)
                        put("name", fullName)
                        put("avatarUrl", doc.getString("photoUrl") ?: "")
                        put("bloodGroup", bloodGroup)
                        put("district", doc.getString("district") ?: "নাটোর")
                        put("upazila", doc.getString("upazila") ?: "লালপুর")
                        put("village", doc.getString("village") ?: "")
                        put("mobile", doc.getString("mobile") ?: "")
                        put("whatsapp", doc.getString("whatsappUsername") ?: "")
                        put("messenger", doc.getString("messengerLink") ?: "")
                        put("isVerified", doc.getBoolean("isVerified") ?: false)
                        put("verifiedUntil", doc.getLong("verifiedUntil") ?: 0L)
                        put("lastDonationTimestamp", lastDonation)
                        put("isVisible", isVisible)
                    }
                    jsonArray.put(obj)
                }
            }

            val internalFile = File(filesDir, "donors_backup.json")
            internalFile.writeText(jsonArray.toString())

            val sahayakDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "sahayak")
            if (!sahayakDir.exists()) sahayakDir.mkdirs()
            val publicFile = File(sahayakDir, "donors_backup.json")
            publicFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error saving donors_backup.json", e)
        }
    }

    private fun saveEmergencyBloodJson(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        try {
            val jsonArray = JSONArray()
            val now = System.currentTimeMillis()

            for (doc in docs) {
                val expiry = FirestoreSafeParser.parseLong(doc.get("expiryTime"), 0L)
                if (expiry == 0L || expiry > now) {
                    val obj = JSONObject().apply {
                        put("id", doc.id)
                        put("userId", FirestoreSafeParser.parseString(doc.get("userId")))
                        put("userName", FirestoreSafeParser.parseString(doc.get("userName"), "রক্তসন্ধানী"))
                        put("userAvatar", FirestoreSafeParser.parseString(doc.get("userAvatar")))
                        put("isVerified", FirestoreSafeParser.parseBoolean(doc.get("isVerified"), false))
                        put("patientName", FirestoreSafeParser.parseString(doc.get("patientName")))
                        put("bloodGroup", FirestoreSafeParser.parseString(doc.get("bloodGroup")))
                        put("bloodAmount", FirestoreSafeParser.parseString(doc.get("bloodAmount"), "১ ব্যাগ"))
                        put("hospitalName", FirestoreSafeParser.parseString(doc.get("hospitalName")))
                        put("locationAddress", FirestoreSafeParser.parseString(doc.get("locationAddress")))
                        put("mobile", FirestoreSafeParser.parseString(doc.get("mobile")))
                        put("whatsapp", FirestoreSafeParser.parseString(doc.get("whatsapp")))
                        put("messenger", FirestoreSafeParser.parseString(doc.get("messenger")))
                        put("description", FirestoreSafeParser.parseString(doc.get("description")))
                        put("uploadtime", FirestoreSafeParser.parseTimestampToMillis(doc.get("uploadtime")))
                        put("expiryTime", expiry)
                        put("status", "active")
                    }
                    jsonArray.put(obj)
                }
            }

            val internalFile = File(filesDir, "emergency_blood_backup.json")
            internalFile.writeText(jsonArray.toString())

            val sahayakDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "sahayak")
            if (!sahayakDir.exists()) sahayakDir.mkdirs()
            val publicFile = File(sahayakDir, "emergency_blood_backup.json")
            publicFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error saving emergency_blood_backup.json", e)
        }
    }
}
