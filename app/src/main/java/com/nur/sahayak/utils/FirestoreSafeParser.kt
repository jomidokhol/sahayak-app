package com.nur.sahayak.utils

import com.google.firebase.Timestamp
import java.util.Date

object FirestoreSafeParser {

    fun parseString(obj: Any?, defaultVal: String = ""): String {
        if (obj == null) return defaultVal
        return when (obj) {
            is String -> obj.trim()
            else -> obj.toString().trim()
        }
    }

    fun parseInt(obj: Any?, defaultVal: Int = 0): Int {
        if (obj == null) return defaultVal
        return when (obj) {
            is Number -> obj.toInt()
            is String -> obj.toIntOrNull() ?: defaultVal
            else -> defaultVal
        }
    }

    fun parseLong(obj: Any?, defaultVal: Long = 0L): Long {
        if (obj == null) return defaultVal
        return when (obj) {
            is Number -> obj.toLong()
            is String -> obj.toLongOrNull() ?: defaultVal
            is Timestamp -> obj.toDate().time
            is Date -> obj.time
            else -> defaultVal
        }
    }

    fun parseBoolean(obj: Any?, defaultVal: Boolean = false): Boolean {
        if (obj == null) return defaultVal
        return when (obj) {
            is Boolean -> obj
            is String -> obj.equals("true", ignoreCase = true)
            is Number -> obj.toInt() != 0
            else -> defaultVal
        }
    }

    fun parseTimestampToMillis(obj: Any?, defaultTime: Long = System.currentTimeMillis()): Long {
        if (obj == null) return defaultTime
        return when (obj) {
            is Timestamp -> obj.toDate().time
            is Date -> obj.time
            is Number -> obj.toLong()
            is String -> obj.toLongOrNull() ?: defaultTime
            else -> defaultTime
        }
    }
}
