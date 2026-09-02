package com.nur.sahayak.models

data class ActiveDonor(
    val uid: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val bloodGroup: String = "",
    val district: String = "নাটোর",
    val upazila: String = "লালপুর",
    val village: String = "",
    val mobile: String = "",
    val whatsapp: String = "",
    val messenger: String = "",
    val isVerified: Boolean = false,
    val verifiedUntil: Long = 0L,
    val lastDonationTimestamp: Long = 0L,
    val isVisible: Boolean = true
) {
    val isUserVerified: Boolean
        get() = isVerified && verifiedUntil > System.currentTimeMillis()

    val isReadyToDonate: Boolean
        get() {
            if (lastDonationTimestamp == 0L) return true
            val ninetyDaysMillis = 90L * 24L * 60L * 60L * 1000L
            return System.currentTimeMillis() >= (lastDonationTimestamp + ninetyDaysMillis)
        }
}
