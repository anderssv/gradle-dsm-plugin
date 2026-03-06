package com.example.service

import com.example.model.User
import com.example.repository.UserRepository

class UserService(private val userRepository: UserRepository) {
    fun register(name: String, email: String): User {
        val user = User(id = System.nanoTime(), name = name, email = email)
        userRepository.save(user)
        return user
    }

    fun getUser(id: Long): User? = userRepository.findById(id)
}
