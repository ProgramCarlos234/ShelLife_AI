package com.example.shelflife_ai.model

data class ProductModel(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = "Abarrotes / Despensa",
    val quantity: Double = 1.0,
    val unit: String = "Unidades",
    val expirationDate: Long = System.currentTimeMillis(),
    val originalExpirationDate: Long = System.currentTimeMillis(),
    val storageType: String = "Almacén / Despensa",
    val imageUrl: String = "",
    val isOpened: Boolean = false,
    val openedDate: Long? = null,
    val openedDaysLimit: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
