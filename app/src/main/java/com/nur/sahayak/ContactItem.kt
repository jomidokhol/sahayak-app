package com.nur.sahayak

data class ContactItem(
    val id: String = "",
    val category: String = "",
    val name: String = "",
    val title: String = "",
    val phone: String = "",
    val location: String = "",
    val whatsapp: String = "",
    val facebook: String = "",
    val isApproved: Boolean = true
)
