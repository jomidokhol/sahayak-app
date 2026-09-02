package com.nur.sahayak.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.util.Log
import com.nur.sahayak.ContactItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object OfflineContactManager {

    private const val PREFS_NAME = "offline_contact_prefs"
    private const val KEY_AUTO_DOWNLOAD = "key_auto_download_contacts"
    private const val KEY_LAST_SYNC_COUNT = "key_last_sync_count"
    private const val FOLDER_NAME = "sahayak"
    private const val FILE_NAME = "contacts.json"

    fun isAutoDownloadEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_DOWNLOAD, true)
    }

    fun setAutoDownloadEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD, enabled).apply()
    }

    fun getLastSyncCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LAST_SYNC_COUNT, 0)
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val actNw = connectivityManager.getNetworkCapabilities(network) ?: return false
            return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    private fun getStorageFiles(context: Context): List<File> {
        val files = mutableListOf<File>()

        try {
            val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME)
            if (!docsDir.exists()) docsDir.mkdirs()
            files.add(File(docsDir, FILE_NAME))
        } catch (e: Exception) {}

        try {
            val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            files.add(File(downloadsDir, FILE_NAME))
        } catch (e: Exception) {}

        try {
            val internalDir = File(context.filesDir, FOLDER_NAME)
            if (!internalDir.exists()) internalDir.mkdirs()
            files.add(File(internalDir, FILE_NAME))
        } catch (e: Exception) {}

        return files
    }

    fun saveContactsToJson(context: Context, contacts: List<ContactItem>): Boolean {
        if (contacts.isEmpty()) return false
        return try {
            val jsonArray = JSONArray()
            for (item in contacts) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("category", item.category)
                    put("name", item.name)
                    put("title", item.title)
                    put("phone", item.phone)
                    put("location", item.location)
                    put("whatsapp", item.whatsapp)
                    put("facebook", item.facebook)
                    put("imageUrl", item.imageUrl)
                    put("isApproved", item.isApproved)
                }
                jsonArray.put(obj)
            }

            val jsonString = jsonArray.toString()
            val targets = getStorageFiles(context)
            for (file in targets) {
                try {
                    if (file.exists()) file.delete()
                    file.writeText(jsonString)
                } catch (e: Exception) {
                    Log.e("OfflineContactManager", "Write error for ${file.absolutePath}", e)
                }
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_LAST_SYNC_COUNT, contacts.size).apply()
            true
        } catch (e: Exception) {
            Log.e("OfflineContactManager", "Save JSON Error", e)
            false
        }
    }

    fun loadContactsFromJson(context: Context): List<ContactItem> {
        val targets = getStorageFiles(context)
        for (file in targets) {
            if (file.exists()) {
                try {
                    val content = file.readText()
                    if (content.isNotEmpty()) {
                        val jsonArray = JSONArray(content)
                        val list = mutableListOf<ContactItem>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            list.add(
                                ContactItem(
                                    id = obj.optString("id"),
                                    category = obj.optString("category"),
                                    name = obj.optString("name"),
                                    title = obj.optString("title"),
                                    phone = obj.optString("phone"),
                                    location = obj.optString("location"),
                                    whatsapp = obj.optString("whatsapp"),
                                    facebook = obj.optString("facebook"),
                                    imageUrl = "", // Default icon offline
                                    isApproved = obj.optBoolean("isApproved", true)
                                )
                            )
                        }
                        if (list.isNotEmpty()) return list
                    }
                } catch (e: Exception) {
                    Log.e("OfflineContactManager", "Read JSON Error", e)
                }
            }
        }
        return emptyList()
    }

    fun hasLocalBackup(context: Context): Boolean {
        val targets = getStorageFiles(context)
        return targets.any { it.exists() && it.length() > 0 }
    }
}
