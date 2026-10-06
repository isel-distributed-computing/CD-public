// Runs a demo container, waits for it to finish and reads its exit code.
//   ./gradlew run
plugins {
    application
}

group = "com.example"
version = "1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val dockerJavaVersion = "3.7.1"

dependencies {
    implementation("com.github.docker-java:docker-java-core:$dockerJavaVersion")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:$dockerJavaVersion")

    // SLF4J binding for docker-java logs (avoids startup warnings).
    runtimeOnly("org.slf4j:slf4j-simple:1.7.36")
}

application {
    mainClass.set("com.example.docker.DockerDemo")
}
