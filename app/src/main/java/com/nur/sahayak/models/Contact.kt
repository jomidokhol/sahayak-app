package com.nur.sahayak.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Contact(
    var id: String = "",
    var name: String = "",
    var category: String = "other",
    var phone: String = "",
    var title: String = "",
    var location: String = "",
    var whatsapp: String? = null,
    var facebook: String? = null,
    var imageUrl: String? = null,
    var isApproved: Boolean = false,
    var createdBy: String? = null
)
