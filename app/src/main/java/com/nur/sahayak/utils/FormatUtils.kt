package com.nur.sahayak.utils

import java.text.DecimalFormat

object FormatUtils {
    fun formatCount(count: Int): String {
        if (count < 1000) return count.toString()
        val exp = (Math.log(count.toDouble()) / Math.log(1000.0)).toInt()
        val format = DecimalFormat("0.#")
        val value = count / Math.pow(1000.0, exp.toDouble())
        return String.format("%s%c", format.format(value), "kMGTPE"[exp - 1])
    }
}
