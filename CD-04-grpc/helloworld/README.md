# gRPC Demo

Java gRPC example using Gradle Kotlin DSL.

## IntelliJ IDEA

1. Open IntelliJ IDEA.
2. Choose **Open** and select the extracted `helloworld` directory
   (or select `build.gradle.kts` and import it as a Gradle project).
3. Let Gradle download the dependencies.
4. Run the `generateProto` Gradle task if IntelliJ has not generated
   the protobuf/gRPC sources automatically.
5. Run `GrpcServer`.
6. In a second run configuration, run `GrpcClient`.

The server listens on localhost:50051.
