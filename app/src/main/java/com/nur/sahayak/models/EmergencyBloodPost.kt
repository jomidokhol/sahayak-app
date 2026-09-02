package com.nur.sahayak.models

data class EmergencyBloodPost(
    val id: String = "",
    val userId: String = "",
    val userName: String = "রক্তসন্ধানী",
    val userAvatar: String = "",
    val isVerified: Boolean = false,
    val patientName: String = "",
    val bloodGroup: String = "",
    val bloodAmount: String = "১ ব্যাগ",
    val hospitalName: String = "",
    val locationAddress: String = "",
    val mobile: String = "",
    val whatsapp: String = "",
    val messenger: String = "",
    val description: String = "",
    val uploadtime: Long = System.currentTimeMillis(),
    val expiryTime: Long = 0L,
    val status: String = "active"
)
