package com.example.api

import com.example.model.OrderItem
import com.example.service.OrderService

class OrderController(private val orderService: OrderService) {
    fun handlePlaceOrder(userId: Long, items: List<OrderItem>) =
        orderService.placeOrder(userId, items)
}
