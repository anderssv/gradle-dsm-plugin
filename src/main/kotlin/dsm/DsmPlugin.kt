package dsm

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin that registers a `dsm` task for generating a
 * Dependency Structure Matrix from compiled bytecode.
 *
 * Configuration via build.gradle.kts:
 * ```
 * plugins {
 *     id("no.f12.dsm") version "0.1.0"
 * }
 *
 * dsm {
 *     rootPackage.set("com.example.myapp")
 *     depth.set(2)
 *     // htmlOutputFile.set(layout.buildDirectory.file("reports/dsm.html"))
 * }
 * ```
 *
 * Run: ./gradlew dsm
 *
 * Command-line overrides via project properties:
 *   ./gradlew dsm -Pdsm.depth=3
 *   ./gradlew dsm -Pdsm.html=build/reports/dsm.html
 */
class DsmPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("dsm", DsmExtension::class.java)

        extension.rootPackage.convention("")
        extension.depth.convention(2)

        project.tasks.register("dsm", DsmTask::class.java).configure {
            classesDir.set(
                extension.classesDir.orElse(
                    project.layout.buildDirectory.dir("classes/kotlin/main"),
                ),
            )
            rootPackage.set(extension.rootPackage)

            // Allow command-line override: -Pdsm.depth=3
            val depthOverride = project.findProperty("dsm.depth")?.toString()?.toIntOrNull()
            if (depthOverride != null) {
                depth.set(depthOverride)
            } else {
                depth.set(extension.depth)
            }

            // Allow command-line override: -Pdsm.html=build/reports/dsm.html
            val htmlOverride = project.findProperty("dsm.html")?.toString()
            if (htmlOverride != null) {
                htmlOutputFile.set(project.layout.projectDirectory.file(htmlOverride))
            } else {
                htmlOutputFile.set(extension.htmlOutputFile)
            }

            dependsOn("classes")
        }
    }
}
