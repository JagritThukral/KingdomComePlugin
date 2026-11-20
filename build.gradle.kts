plugins {
    id("java")
}

group = "dev.jagritthukral"
version = "1.0-SNAPSHOT"

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