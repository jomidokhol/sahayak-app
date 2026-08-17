package com.nur.sahayak.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class ReplyPost(
    var id: String = "",
    var userid: String = "",
    var userName: String = "লালপুরবাসী",
    var userAvatar: String = "",
    var content: String = "",
    var uploadtime: Long = System.currentTimeMillis(),
    var isEdited: Boolean = false
)
