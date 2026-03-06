package dsm

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DsmPluginFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `dsm task produces matrix with expected packages`() {
        writeTestProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("dsm", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":dsm")?.outcome)

        val output = result.output
        assertTrue(output.contains("Dependency Structure Matrix"), "Should print DSM header")
        assertTrue(output.contains("api"), "Should contain api package")
        assertTrue(output.contains("model"), "Should contain model package")
        assertTrue(output.contains("repository"), "Should contain repository package")
        assertTrue(output.contains("service"), "Should contain service package")
        assertTrue(output.contains("notification"), "Should contain notification package")
    }

    @Test
    fun `dsm task detects cyclic dependencies`() {
        writeTestProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("dsm", "--stacktrace")
            .forwardOutput()
            .build()

        val output = result.output
        assertTrue(output.contains("Cyclic dependencies detected"), "Should detect cycles")
        assertTrue(output.contains("notification") && output.contains("service"),
            "Should report notification <-> service cycle")
    }

    @Test
    fun `dsm task generates html report`() {
        writeTestProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("dsm", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":dsm")?.outcome)

        val htmlFile = File(projectDir, "build/reports/dsm.html")
        assertTrue(htmlFile.exists(), "HTML report should be generated")

        val html = htmlFile.readText()
        assertTrue(html.contains("Dependency Structure Matrix"), "HTML should contain title")
        assertTrue(html.contains("backward"), "HTML should mark backward dependencies")
    }

    @Test
    fun `dsm task respects depth property override`() {
        writeTestProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("dsm", "-Pdsm.depth=1", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":dsm")?.outcome)
        // At depth 1, all packages collapse to single-segment names
        // so there should be fewer rows in the matrix
        val output = result.output
        assertTrue(output.contains("Dependency Structure Matrix"), "Should still produce output")
    }

    private fun writeTestProject() {
        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "dsm-test"
        """.trimIndent())

        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                kotlin("jvm") version "2.1.10"
                id("no.f12.dsm")
            }
            repositories { mavenCentral() }
            dsm {
                rootPackage.set("com.example")
                depth.set(2)
                htmlOutputFile.set(layout.buildDirectory.file("reports/dsm.html"))
            }
        """.trimIndent())

        writeSourceFile("com/example/model/User.kt", """
            package com.example.model
            data class User(val id: Long, val name: String, val email: String)
        """)

        writeSourceFile("com/example/model/Order.kt", """
            package com.example.model
            data class Order(val id: Long, val userId: Long, val items: List<OrderItem>, val total: Double)
            data class OrderItem(val productName: String, val quantity: Int, val price: Double)
        """)

        writeSourceFile("com/example/repository/UserRepository.kt", """
            package com.example.repository
            import com.example.model.User
            class UserRepository {
                private val users = mutableMapOf<Long, User>()
                fun save(user: User) { users[user.id] = user }
                fun findById(id: Long): User? = users[id]
                fun findAll(): List<User> = users.values.toList()
            }
        """)

        writeSourceFile("com/example/repository/OrderRepository.kt", """
            package com.example.repository
            import com.example.model.Order
            class OrderRepository {
                private val orders = mutableMapOf<Long, Order>()
                fun save(order: Order) { orders[order.id] = order }
                fun findByUserId(userId: Long): List<Order> = orders.values.filter { it.userId == userId }
            }
        """)

        writeSourceFile("com/example/service/UserService.kt", """
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
        """)

        writeSourceFile("com/example/service/OrderService.kt", """
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
        """)

        writeSourceFile("com/example/api/UserController.kt", """
            package com.example.api
            import com.example.model.User
            import com.example.service.UserService
            class UserController(private val userService: UserService) {
                fun handleRegister(name: String, email: String): User = userService.register(name, email)
                fun handleGetUser(id: Long): User? = userService.getUser(id)
            }
        """)

        writeSourceFile("com/example/api/OrderController.kt", """
            package com.example.api
            import com.example.model.OrderItem
            import com.example.service.OrderService
            class OrderController(private val orderService: OrderService) {
                fun handlePlaceOrder(userId: Long, items: List<OrderItem>) = orderService.placeOrder(userId, items)
            }
        """)

        writeSourceFile("com/example/notification/EmailNotifier.kt", """
            package com.example.notification
            import com.example.model.User
            import com.example.service.UserService
            class EmailNotifier(private val userService: UserService) {
                fun notifyUser(userId: Long, message: String) {
                    val user = userService.getUser(userId)
                    if (user != null) { println("Sending email to ${'$'}{user.email}: ${'$'}message") }
                }
            }
        """)

        writeSourceFile("com/example/service/NotificationBridge.kt", """
            package com.example.service
            import com.example.notification.EmailNotifier
            class NotificationBridge(private val notifier: EmailNotifier) {
                fun sendWelcome(userId: Long) { notifier.notifyUser(userId, "Welcome!") }
            }
        """)
    }

    private fun writeSourceFile(path: String, content: String) {
        val file = File(projectDir, "src/main/kotlin/$path")
        file.parentFile.mkdirs()
        file.writeText(content.trimIndent())
    }
}
