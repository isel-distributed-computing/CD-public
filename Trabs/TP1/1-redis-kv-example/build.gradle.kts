// Minimal Redis key/value example (Jedis). Start Redis first with start-redis.sh.
//   ./gradlew run [--args="host port"]
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

dependencies {
    implementation("redis.clients:jedis:8.0.1")

    // SLF4J binding for Jedis logs (avoids startup warnings).
    runtimeOnly("org.slf4j:slf4j-simple:1.7.36")
}

application {
    mainClass.set("com.example.redis.RedisKeyValueExample")
}
