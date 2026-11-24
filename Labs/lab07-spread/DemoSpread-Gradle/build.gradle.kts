plugins {
    java
    application
}

group = "demospread"
version = "1.0"

repositories {
    mavenCentral()
}

// Compila para Java 21 com qualquer JDK >= 21 instalado
tasks.withType<JavaCompile> {
    options.release.set(21)
}

dependencies {
    // A biblioteca Java do Spread 5.0.1 não está no Maven Central:
    // o jar é distribuído com o projeto, na diretoria libs/
    implementation(files("libs/spread-5.0.1.jar"))
}

application {
    mainClass.set("demospread.DemoSpread")
}

// "gradle run --args=<IP do daemon>": o menu lê do teclado
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// "gradle jar" gera build/libs/DemoSpread.jar com o Spread incluído,
// para correr vários membros em terminais diferentes com "java -jar"
tasks.jar {
    archiveVersion.set("")   // nome fixo: DemoSpread.jar (sem a versão)
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
