plugins {
    java
}

group = "com.zpkdxgames"
version = "1.2.0"

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
    compileOnly("io.papermc.paper:paper-api:26.2.build.65-beta")
    compileOnly("com.zpkdxgames:PlexonCore:1.0.0")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
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
