package com.example.service

import com.example.model.Order
import com.example.model.OrderItem
import com.example.repository.OrderRepository

class OrderService(private val orderRepository: OrderRepository) {
    fun placeOrder(userId: Long, items: List<OrderItem>): Order {
        val total = items.sumOf { it.price * it.quantity }
        val order = Order(id = System.nanoTime(), userId = userId, items = items, total = total)
        orderRepository.save(order)
        return order
    }
}
