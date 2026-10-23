package com.example.shelflife_ai.model

data class UserModel(
    val uid: String = "",
    val email: String = "",
    val phone: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
