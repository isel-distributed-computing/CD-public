# coderunner-lab

Esqueleto do projeto Gradle para o Laboratório de Preparação do TPA1 (CodeRunner).

## Abrir no IntelliJ

`File → Open…` e escolher a pasta `coderunner-lab` (ou o `build.gradle.kts`).
Em *Settings → Build Tools → Gradle*, confirmar que o *Gradle JVM* é um JDK 21.

## Linha de comandos

```bash
./gradlew classes      # Linux/macOS
gradlew.bat classes    # Windows
./gradlew run          # depois de criar lab/RedisExample.java
```

## Por fazer (ver enunciado do laboratório)

- `src/main/java/lab/RedisExample.java` (secção 6)
- `work/prog.py`, `work/input.txt` (secção 7)
- `work/soma.py`, `work/erro.py`, `work/ciclo.py`, `work/memoria.py`, `work/soma-input.txt` (secção 10)
- Experiências com `docker-java` (secção 12)
