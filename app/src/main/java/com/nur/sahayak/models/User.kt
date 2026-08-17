package com.nur.sahayak.models

data class User(
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val age: Int = 0,
    val mobile: String = "",
    val email: String = "",
    val role: String = "user"
)
