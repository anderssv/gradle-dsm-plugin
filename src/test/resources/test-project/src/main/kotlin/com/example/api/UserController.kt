package com.example.api

import com.example.model.User
import com.example.service.UserService

class UserController(private val userService: UserService) {
    fun handleRegister(name: String, email: String): User =
        userService.register(name, email)

    fun handleGetUser(id: Long): User? =
        userService.getUser(id)
}
