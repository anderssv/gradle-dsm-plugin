package com.example.repository

import com.example.model.User

class UserRepository {
    private val users = mutableMapOf<Long, User>()

    fun save(user: User) {
        users[user.id] = user
    }

    fun findById(id: Long): User? = users[id]

    fun findAll(): List<User> = users.values.toList()
}
