plugins {
    kotlin("jvm") version "2.1.10"
    id("no.f12.dsm") version "0.1.0"
}

repositories {
    mavenCentral()
}

dsm {
    rootPackage.set("com.example")
    depth.set(2)
    htmlOutputFile.set(layout.buildDirectory.file("reports/dsm.html"))
}
