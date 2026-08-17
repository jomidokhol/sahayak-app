package com.nur.sahayak.utils

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

object ImgBBUploader {

    private const val DEFAULT_API_KEY = "6d7007326130a08e1a026e6d15a95f5e"

    fun uploadBitmap(bitmap: Bitmap, apiKey: String = DEFAULT_API_KEY, callback: (String?) -> Unit) {
        Thread {
            try {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val imageBytes = baos.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

                val keyToUse = if (apiKey.isNotEmpty()) apiKey else DEFAULT_API_KEY
                val urlStr = "https://api.imgbb.com/1/upload?key=$keyToUse"
                val url = URL(urlStr)

                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.doInput = true

                val postData = "image=" + URLEncoder.encode(base64Image, "UTF-8")
                val outputStream = conn.outputStream
                outputStream.write(postData.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                outputStream.close()

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream: InputStream = conn.inputStream
                    val responseText = inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = JSONObject(responseText)
                    val dataObj = jsonObj.getJSONObject("data")
                    val imageUrl = dataObj.getString("url")
                    callback(imageUrl)
                } else {
                    Log.e("ImgBBUploader", "Upload Failed Code: $responseCode")
                    callback(null)
                }
            } catch (e: Exception) {
                Log.e("ImgBBUploader", "Upload Exception", e)
                callback(null)
            }
        }.start()
    }
}
