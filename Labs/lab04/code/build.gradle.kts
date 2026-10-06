plugins {
    java
    application
}

group = "pt.cd"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("redis.clients:jedis:7.5.3")
    implementation("com.github.docker-java:docker-java-core:3.7.1")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.7.1")
}

application {
    mainClass = "lab.RedisExample"
}
