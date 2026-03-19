package dsm

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DsmPluginFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        // Copy source files from test resources
        val testProjectSrc = File(javaClass.classLoader.getResource("test-project/src")!!.toURI())
        testProjectSrc.copyRecursively(File(projectDir, "src"))

        // Write minimal build files for TestKit (withPluginClasspath injects the plugin)
        File(projectDir, "settings.gradle.kts").writeText(
            """rootProject.name = "dsm-test"""",
        )

        File(projectDir, "build.gradle.kts").writeText(
            """
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
            """.trimIndent(),
        )
    }

    private fun runDsm(vararg extraArgs: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments("dsm", "--stacktrace", *extraArgs)
        .forwardOutput()
        .build()

    @Test
    fun `dsm task produces matrix with expected packages`() {
        val result = runDsm()

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
        val result = runDsm()

        val output = result.output
        assertTrue(output.contains("Cyclic dependencies detected"), "Should detect cycles")
        assertTrue(
            output.contains("notification") && output.contains("service"),
            "Should report notification <-> service cycle",
        )
    }

    @Test
    fun `dsm task generates html report`() {
        val result = runDsm()

        assertEquals(TaskOutcome.SUCCESS, result.task(":dsm")?.outcome)

        val htmlFile = File(projectDir, "build/reports/dsm.html")
        assertTrue(htmlFile.exists(), "HTML report should be generated")

        val html = htmlFile.readText()
        assertTrue(html.contains("Dependency Structure Matrix"), "HTML should contain title")
        assertTrue(html.contains("backward"), "HTML should mark backward dependencies")
    }

    @Test
    fun `dsm task respects depth property override`() {
        val result = runDsm("-Pdsm.depth=1")

        assertEquals(TaskOutcome.SUCCESS, result.task(":dsm")?.outcome)
        val output = result.output
        assertTrue(output.contains("Dependency Structure Matrix"), "Should still produce output")
    }
}
