package com.example.model

data class Order(
    val id: Long,
    val userId: Long,
    val items: List<OrderItem>,
    val total: Double,
)

data class OrderItem(
    val productName: String,
    val quantity: Int,
    val price: Double,
)
