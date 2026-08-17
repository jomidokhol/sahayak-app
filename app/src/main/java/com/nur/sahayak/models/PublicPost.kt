package com.nur.sahayak.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class PublicPost(
    var id: String = "",
    var userId: String = "",
    var userName: String = "লালপুরবাসী",
    var userAvatar: String = "",
    var content: String = "",
    var timestamp: Long = System.currentTimeMillis()
)
