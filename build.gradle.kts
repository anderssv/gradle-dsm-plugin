plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "no.f12"
version = "0.1.0"

gradlePlugin {
    website.set("https://github.com/anderssv/gradle-dsm-plugin")
    vcsUrl.set("https://github.com/anderssv/gradle-dsm-plugin")

    plugins {
        create("dsm") {
            id = "no.f12.dsm"
            displayName = "DSM - Dependency Structure Matrix"
            description = "Analyzes compiled JVM bytecode to produce a package dependency matrix. " +
                "Useful for identifying cyclic dependencies, measuring coupling, and guiding refactoring."
            tags.set(listOf("architecture", "dependencies", "analysis", "dsm", "coupling"))
            implementationClass = "dsm.DsmPlugin"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.7.1")
}
