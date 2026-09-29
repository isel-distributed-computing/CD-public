import com.google.protobuf.gradle.id

plugins {
    `java-library`
    `maven-publish`
    // o plugin deteta o sistema operativo e descarrega o protoc/protoc-gen-grpc-java certos.
    id("com.google.protobuf") version "0.10.0"
}

// Coordenadas iguais às do pom.xml (isel.grpc:grpcContract:1.0)
group = "isel.grpc"
version = "1.0"

val grpcVersion = "1.84.0"
val protocVersion = "3.25.9"

repositories {
    mavenCentral()
}

dependencies {
    // "api" torna as dependências transitivas
    // o cliente e o servidor recebem o gRPC apenas por dependerem do contrato.
    api("javax.annotation:javax.annotation-api:1.3.2")
    api("io.grpc:grpc-netty-shaded:$grpcVersion")
    api("io.grpc:grpc-protobuf:$grpcVersion")
    api("io.grpc:grpc-stub:$grpcVersion")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protocVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc") { }
            }
        }
    }
}

// Permite "./gradlew publishToMavenLocal" (equivalente a "mvn install"),
// para usar o contrato a partir do repositório local ~/.m2.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
