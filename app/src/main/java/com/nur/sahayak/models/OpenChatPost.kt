package com.nur.sahayak.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class OpenChatPost(
    var id: String = "",
    var userid: String = "",
    var userName: String = "লালপুরবাসী",
    var userAvatar: String = "",
    var content: String = "",
    var uploadtime: Long = System.currentTimeMillis(),
    var likesCount: Int = 0,
    var repliesCount: Int = 0,
    var likedBy: List<String> = emptyList()
)
