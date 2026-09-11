import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
}

group = "com.zpkdxgames"
version = "2.0.0-rc.2"

val productionPaperVersion = "26.2.build.121-stable"
val mockBukkitPaperVersion = "26.2.build.111-stable"

repositories {
    maven {
        name = "plexonCoreLocal"
        url = uri(layout.projectDirectory.dir(".deps/repository"))
    }
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    // Production compilation remains pinned to the exact PlexonCraft target.
    compileOnly("io.papermc.paper:paper-api:$productionPaperVersion")
    compileOnly("com.zpkdxgames:PlexonCore:2.0.4")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.zpkdxgames:PlexonCore:2.0.4")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.116.1")
    // MockBukkit 4.116.1 was built against Paper 26.2.build.111-stable.
    // Test-only classpaths are intentionally binary-aligned to that API while
    // production compileJava remains pinned to build.121 above.
    testImplementation("io.papermc.paper:paper-api:$mockBukkitPaperVersion")
}

configurations.named("testCompileClasspath") {
    resolutionStrategy.force("io.papermc.paper:paper-api:$mockBukkitPaperVersion")
}
configurations.named("testRuntimeClasspath") {
    resolutionStrategy.force("io.papermc.paper:paper-api:$mockBukkitPaperVersion")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
            showCauses = true
            showExceptions = true
            showStackTraces = true
        }
    }

    jar {
        archiveBaseName.set("PlexonBackpacks")
        archiveVersion.set(project.version.toString())
    }
}
