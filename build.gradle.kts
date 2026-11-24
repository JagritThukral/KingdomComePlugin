plugins {
    id("java")
}

group = "dev.jagritthukral"
version = "0.1.6-SNAPSHOT"

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
        expand(props)
    }
}

repositories {
    mavenCentral()
    maven(url = "https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly("com.zaxxer:HikariCP:7.1.0")
    compileOnly("org.jctools:jctools-core:4.0.7")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
