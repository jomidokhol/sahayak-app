package com.nur.sahayak.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class News(
    var id: String = "",
    var title: String = "",
    var reporter: String = "",
    var imageUrl: String = "",
    var desc: String = "",
    var viewCount: Int = 0,
    var timestamp: Long = System.currentTimeMillis()
)
