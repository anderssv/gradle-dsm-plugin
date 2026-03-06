package com.example.repository

import com.example.model.Order

class OrderRepository {
    private val orders = mutableMapOf<Long, Order>()

    fun save(order: Order) {
        orders[order.id] = order
    }

    fun findByUserId(userId: Long): List<Order> =
        orders.values.filter { it.userId == userId }
}
