plugins {
    id("java")
}

group = "dev.jagritthukral"
version = "1.0-SNAPSHOT"

repositories {
    maven(url = "https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    implementation("com.zaxxer:HikariCP:4.0.3")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}