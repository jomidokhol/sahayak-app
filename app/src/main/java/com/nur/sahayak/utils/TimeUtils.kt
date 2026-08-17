package com.nur.sahayak.utils

object TimeUtils {
    fun getTimeAgo(time: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - time

        val second = 1000L
        val minute = 60 * second
        val hour = 60 * minute
        val day = 24 * hour

        return when {
            diff < minute -> "${diff / second} সেকেন্ড আগে"
            diff < 2 * minute -> "১ মিনিট আগে"
            diff < hour -> "${diff / minute} মিনিট আগে"
            diff < 2 * hour -> "১ ঘণ্টা আগে"
            diff < day -> "${diff / hour} ঘণ্টা আগে"
            diff < 2 * day -> "গতকাল"
            else -> "${diff / day} দিন আগে"
        }
    }
}
