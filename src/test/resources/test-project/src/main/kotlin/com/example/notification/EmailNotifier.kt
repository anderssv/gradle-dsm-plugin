package com.example.notification

import com.example.model.User
import com.example.service.UserService

/**
 * This class deliberately creates a cycle: notification -> service -> repository -> model,
 * but notification also depends on service, while service could depend back on notification
 * via the listener pattern below.
 */
class EmailNotifier(private val userService: UserService) {
    fun notifyUser(userId: Long, message: String) {
        val user = userService.getUser(userId)
        if (user != null) {
            println("Sending email to ${user.email}: $message")
        }
    }
}
