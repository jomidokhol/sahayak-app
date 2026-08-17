package com.nur.sahayak.utils

object FirebaseParser {
    fun parseString(value: Any?, default: String = ""): String {
        return value?.toString() ?: default
    }

    fun parseLong(value: Any?, default: Long = 0L): Long {
        if (value == null) return default
        return when (value) {
            is Long -> value
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: default
            else -> default
        }
    }

    fun parseInt(value: Any?, default: Int = 0): Int {
        if (value == null) return default
        return when (value) {
            is Int -> value
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }

    fun parseBoolean(value: Any?, default: Boolean = true): Boolean {
        if (value == null) return default
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> default
        }
    }
}
