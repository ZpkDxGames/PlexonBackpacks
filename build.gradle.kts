plugins {
    java
}

group = "com.zpkdxgames"
version = "1.2.1"

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
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    compileOnly("com.zpkdxgames:PlexonCore:2.0.4")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.2.build.121-stable")
    testImplementation("com.zpkdxgames:PlexonCore:2.0.4")
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
    }

    jar {
        archiveBaseName.set("PlexonBackpacks")
        archiveVersion.set(project.version.toString())
    }
}
