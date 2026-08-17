package com.nur.sahayak.utils

import com.google.firebase.Timestamp
import java.util.Date

object FirestoreSafeParser {

    fun parseTimestampToMillis(obj: Any?, defaultMillis: Long = System.currentTimeMillis()): Long {
        if (obj == null) return defaultMillis
        return when (obj) {
            is Timestamp -> obj.toDate().time
            is Long -> obj
            is Double -> obj.toLong()
            is Int -> obj.toLong()
            is Number -> obj.toLong()
            is Date -> obj.time
            is String -> obj.toLongOrNull() ?: defaultMillis
            else -> defaultMillis
        }
    }

    fun parseString(obj: Any?, defaultStr: String = ""): String {
        return obj?.toString() ?: defaultStr
    }

    fun parseInt(obj: Any?, defaultInt: Int = 0): Int {
        if (obj == null) return defaultInt
        return when (obj) {
            is Number -> obj.toInt()
            is String -> obj.toIntOrNull() ?: defaultInt
            else -> defaultInt
        }
    }

    fun parseBoolean(obj: Any?, defaultBool: Boolean = true): Boolean {
        if (obj == null) return defaultBool
        return when (obj) {
            is Boolean -> obj
            is String -> obj.equals("true", ignoreCase = true)
            is Number -> obj.toInt() != 0
            else -> defaultBool
        }
    }
}
