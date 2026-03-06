package com.example.service

import com.example.notification.EmailNotifier

/**
 * Creates a cyclic dependency: service -> notification and notification -> service.
 * This is intentional to test that the DSM plugin detects and reports cycles.
 */
class NotificationBridge(private val notifier: EmailNotifier) {
    fun sendWelcome(userId: Long) {
        notifier.notifyUser(userId, "Welcome!")
    }
}
