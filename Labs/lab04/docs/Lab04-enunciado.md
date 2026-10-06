# Computação Distribuída --- Laboratório 04 --- Preparação do TPA1: CodeRunner

## 1. Objetivos

Este laboratório prepara o ambiente local para o **TPA1 --- CodeRunner**
e permite experimentar os mecanismos apresentados nos Anexos 1, 2 e 3.

No final deverá conseguir:

-   executar Docker e obter a imagem `python:3.12-slim`;
-   lançar e utilizar o Redis;
-   experimentar `RedisClient`;
-   executar programas Python em containers isolados;
-   usar volumes, timeout e limites de memória;
-   experimentar o ciclo de execução apresentado no Anexo 2;
-   testar os casos `CONCLUIDO`, `ERRO` e `TIMEOUT`.

> O objetivo não é implementar o TPA1. É validar o ambiente e
> compreender, isoladamente, o código que depois será adaptado ao
> `RunServer`.

------------------------------------------------------------------------

# Parte I --- Preparação

## 2. Verificar o ambiente

Execute:

``` bash
java -version
docker --version
docker info
docker run --rm hello-world
```

Todos os comandos deverão terminar sem erros.

### 2.1 Software necessário

Antes de iniciar o laboratório, confirme que tem instalado:

- **JDK 21** (https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html);
- **Docker Desktop** (https://www.docker.com/products/docker-desktop/);
- **IntelliJ IDEA** (recomendado).

Não é necessário instalar o Gradle: o projeto inclui o **Gradle Wrapper**
(`gradlew` / `gradlew.bat`), que descarrega automaticamente a versão
correta do Gradle na primeira utilização.

Caso algum destes componentes não esteja instalado, deverá instalá-lo antes de continuar.

Obtenha antecipadamente as imagens necessárias:

``` bash
docker pull python:3.12-slim
docker pull redis
```

Teste Python:

``` bash
docker run --rm python:3.12-slim python --version
```

### Questão 1

Porque deve a imagem `python:3.12-slim` ser obtida antecipadamente?

------------------------------------------------------------------------

# Parte II --- Anexo 1: Redis

## 3. Lançar o Redis

O Anexo 1 lança o servidor com:

``` bash
docker run -d --name ServerRedis -p 6000:6379 redis
```

Confirme:

``` bash
docker ps
docker exec ServerRedis redis-cli PING
```

Resultado esperado:

``` text
PONG
```

### Questão 2

O que significa `-p 6000:6379`?

------------------------------------------------------------------------

## 4. Experimentar o estado de um pedido

Entre no Redis:

``` bash
docker exec -it ServerRedis redis-cli
```

Execute:

``` text
HSET pedido:123 estado PENDENTE servidor 10.0.0.5:8500
HGETALL pedido:123
EXPIRE pedido:123 86400
TTL pedido:123
```

Simule a conclusão:

``` text
HSET pedido:123 estado CONCLUIDO exitCode 0 stdout "42" stderr ""
HGETALL pedido:123
```

Saia:

``` text
QUIT
```

### Questão 3

Porque é adequado guardar cada pedido num hash Redis?

### Questão 4

Porque é definida uma expiração de 24 horas?

------------------------------------------------------------------------

# Parte III --- Código Java do Anexo 1

## 5. Abrir o projeto base

O projeto Gradle `coderunner-lab` é fornecido em anexo, com a seguinte
estrutura:

``` text
coderunner-lab/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── work/
└── src/main/java/lab/
```

O `build.gradle.kts` (Gradle Kotlin DSL) já está configurado com o Java 21
(toolchain), o plugin `application` (classe principal `lab.RedisExample`)
e as dependências necessárias:

| Biblioteca                          | Versão |
| ----------------------------------- | -----: |
| `redis.clients:jedis`               |  7.5.3 |
| `docker-java-core`                  |  3.4.0 |
| `docker-java-transport-httpclient5` |  3.4.0 |

As versões do `docker-java` são as indicadas nos anexos. Para o Jedis é
usada a versão 7.5.3, porque a classe `RedisClient` só existe a partir do
Jedis 7.2.0 (nas versões anteriores usava-se `JedisPooled`, entretanto
descontinuada).

Não é necessário alterar os ficheiros de configuração nem os do wrapper.
O build é sempre feito **no IntelliJ** ou **através do wrapper**; não use
um comando `gradle` instalado localmente, para garantir que todos usam a
mesma versão do Gradle.

Abra o projeto no IntelliJ (`File → Open…`, escolhendo a pasta
`coderunner-lab`) e aguarde a sincronização do Gradle. Em
*Settings → Build, Execution, Deployment → Build Tools → Gradle*,
confirme que o *Gradle JVM* é um JDK 21.

Em alternativa, na linha de comandos:

```bash
./gradlew classes        # Linux/macOS
gradlew.bat classes      # Windows
```

------------------------------------------------------------------------

## 6. Experimentar `RedisClient`

Crie `src/main/java/lab/RedisExample.java`:

``` java
package lab;

import redis.clients.jedis.RedisClient;
import java.util.Map;
import java.util.UUID;

public class RedisExample {

    public static void main(String[] args) {

        RedisClient jedis = RedisClient.create("localhost", 6000);

        String id = UUID.randomUUID().toString();
        String key = "pedido:" + id;

        jedis.hset(key, Map.of(
                "estado", "PENDENTE",
                "servidor", "10.0.0.5:8500"
        ));

        jedis.expire(key, 24 * 3600);

        System.out.println(jedis.hgetAll(key));

        jedis.hset(key, Map.of(
                "estado", "CONCLUIDO",
                "exitCode", "0",
                "stdout", "42",
                "stderr", ""
        ));

        System.out.println(jedis.hgetAll(key));
        jedis.close();
    }
}
```

Compile e execute no IntelliJ: na janela *Gradle*, tarefa
*Tasks → application → run* (ou o botão ▶ junto ao método `main`).

Em alternativa, na linha de comandos:

``` bash
./gradlew run            # Linux/macOS
gradlew.bat run          # Windows
```

Confirme diretamente no Redis:

``` bash
docker exec -it ServerRedis redis-cli
```

``` text
HGETALL pedido:<UUID>
```

### Questão 5

Porque é usado um UUID para cada pedido?

### Questão 6

Porque indica o enunciado `RedisClient` em vez de `Jedis`?

------------------------------------------------------------------------

# Parte IV --- Anexo 2: executar um programa num container

## 7. Preparar o programa

Crie `work/prog.py`:

``` python
numbers = list(map(int, input().split()))
print(sum(numbers))
```

Crie `work/input.txt`:

``` text
10 20 30
```

------------------------------------------------------------------------

## 8. Executar através de `/work`

Em Linux/macOS:

``` bash
docker run --rm \
  -v "$(pwd)/work:/work" \
  python:3.12-slim \
  sh -c "python /work/prog.py < /work/input.txt"
```

Resultado esperado:

``` text
60
```

### Questão 7

Qual é a função de `-v ...:/work`?

------------------------------------------------------------------------

## 9. Reproduzir o comando do Anexo 2

Execute:

``` bash
docker run --name run-lab \
  --network none \
  --memory 256m \
  --memory-swap 256m \
  -v "$(pwd)/work:/work" \
  python:3.12-slim \
  sh -c "timeout 10 python /work/prog.py < /work/input.txt > /work/out.txt 2> /work/err.txt; echo \$? > /work/exit.txt"
```

Consulte:

``` bash
cat work/out.txt
cat work/err.txt
cat work/exit.txt
```

Resultado esperado:

``` text
out.txt  -> 60
err.txt  -> vazio
exit.txt -> 0
```

Observe o container:

``` bash
docker ps -a
```

Remova-o:

``` bash
docker rm run-lab
```

### Questão 8

Porque são usados `--network none` e `--memory 256m`?

### Questão 9

Porque são os resultados escritos em ficheiros dentro de `/work`?

------------------------------------------------------------------------

# Parte V --- Anexo 3: programas de teste

## 10. Criar os testes

O Anexo 3 define os seguintes resultados:

| Programa     | Comportamento         | Estado      | Exit code |
| ------------ | --------------------- | ----------- | --------: |
| `soma.py`    | soma valores do stdin | `CONCLUIDO` |         0 |
| `erro.py`    | divisão por zero      | `ERRO`      |         1 |
| `ciclo.py`   | ciclo infinito        | `TIMEOUT`   |       124 |
| `memoria.py` | excede a memória      | `ERRO`      |       137 |

### `soma.py`

O PDF descreve o comportamento, mas não apresenta o código. Para o
laboratório:

``` python
import sys
numeros = [int(x) for x in sys.stdin.read().split()]
print(sum(numeros))
```

### `erro.py`

``` python
print(1 / 0)
```

### `ciclo.py`

Este é o código apresentado no Anexo 3:

``` python
i = 0
while True:
    i += 1
    if i % 10_000_000 == 0:
        print(f"iteracao {i}", flush=True)
```

### `memoria.py`

O PDF descreve o comportamento mas não apresenta o código. Para o
laboratório:

``` python
blocos = []
while True:
    blocos.append(bytearray(10 * 1024 * 1024))
```

Crie ainda `work/soma-input.txt`:

``` text
10 20 30 40
```

------------------------------------------------------------------------

## 11. Executar os quatro casos

Para cada teste, coloque o programa a executar em `work/prog.py`.

Exemplo:

``` bash
cp work/ciclo.py work/prog.py
```

Depois execute:

``` bash
docker run --rm \
  --network none \
  --memory 256m \
  --memory-swap 256m \
  -v "$(pwd)/work:/work" \
  python:3.12-slim \
  sh -c "timeout 10 python /work/prog.py < /work/input.txt > /work/out.txt 2> /work/err.txt; echo \$? > /work/exit.txt"
```

Para `soma.py`, coloque `soma-input.txt` em `input.txt`:

``` bash
cp work/soma-input.txt work/input.txt
```

Depois de cada execução:

``` bash
cat work/out.txt
cat work/err.txt
cat work/exit.txt
```

Preencha:

| Programa     | Exit code observado | Estado esperado |
| ------------ | ------------------: | --------------- |
| `soma.py`    |                     | `CONCLUIDO`     |
| `erro.py`    |                     | `ERRO`          |
| `ciclo.py`   |                     | `TIMEOUT`       |
| `memoria.py` |                     | `ERRO`          |

### Questão 10

Como deverá o `RunServer` transformar os códigos de saída em estados?

------------------------------------------------------------------------

# Parte VI --- Código `docker-java` do Anexo 2

## 12. Experimentar `ContainerRunner`

O Anexo 2 mostra como o `RunServer` deverá fazer em Java aquilo que
acabou de executar manualmente.

O núcleo do código é:

``` java
HostConfig hostConfig = HostConfig.newHostConfig()
    .withBinds(new Bind(work.toString(), new Volume("/work")))
    .withNetworkMode("none")
    .withMemory(256L * 1024 * 1024)
    .withMemorySwap(256L * 1024 * 1024);

String containerId = docker.createContainerCmd("python:3.12-slim")
    .withName("run-" + pedidoId)
    .withUser(uidGid)
    .withHostConfig(hostConfig)
    .withCmd("sh", "-c", COMMAND)
    .exec().getId();

docker.startContainerCmd(containerId).exec();

while (!"exited".equals(
        docker.inspectContainerCmd(containerId)
              .exec()
              .getState()
              .getStatus())) {

    Thread.sleep(500);
}

int exitCode = Integer.parseInt(
        Files.readString(work.resolve("exit.txt")).trim()
);

docker.removeContainerCmd(containerId)
      .withForce(true)
      .exec();
```

Compare este código com o comando Docker executado anteriormente.

Identifique no código Java onde são configurados:

1.  `/work`;
2.  rede;
3.  memória;
4.  imagem;
5.  nome do container;
6.  utilizador;
7.  comando;
8.  polling do estado;
9.  leitura do resultado;
10. remoção.

### Questão 11

Porque deve a remoção do container ocorrer num bloco `finally` na
implementação final?

### Questão 12

Porque indica o enunciado que este método deverá ser executado numa
thread separada?

------------------------------------------------------------------------

# Parte VII --- Relação entre os anexos e o TPA1

## 13. Ciclo completo

Os exercícios anteriores correspondem ao seguinte fluxo:

``` text
Cliente
  |
  v
RunServer
  |
  +--> gera UUID
  |
  +--> Redis: PENDENTE
  |
  +--> devolve UUID ao cliente
  |
  +--> execução em thread separada
          |
          v
      Container Docker
      - python:3.12-slim
      - sem rede
      - 256 MB
      - timeout 10 s
      - /work
          |
          v
      out.txt
      err.txt
      exit.txt
          |
          v
      Redis
      CONCLUIDO / TIMEOUT / ERRO
          |
          v
      remove container
```

### Questão 13

Porque deve o UUID ser devolvido ao cliente antes de a execução
terminar?

### Questão 14

Porque pode um `RunServer` responder sobre um pedido executado por
outro?

------------------------------------------------------------------------

# Parte VIII --- Cleanup e checklist

## 14. Limpar o ambiente

``` bash
docker ps -a
docker stop ServerRedis
docker rm ServerRedis
```

Pode manter as imagens `redis` e `python:3.12-slim`.

------------------------------------------------------------------------

## 15. Checklist

Antes de iniciar o TPA1, confirme:

-   [ ] Docker funciona;
-   [ ] `python:3.12-slim` está disponível;
-   [ ] consigo lançar a BD Redis num contentor de nome `ServerRedis`;
-   [ ] experimentei hashes Redis;
-   [ ] executei código Java com `RedisClient`;
-   [ ] montei uma diretoria em `/work`;
-   [ ] executei código sem rede e limitado a 256 MB;
-   [ ] apliquei timeout de 10 segundos;
-   [ ] produzi `out.txt`, `err.txt` e `exit.txt`;
-   [ ] executei os quatro comportamentos do Anexo 3;
-   [ ] analisei o código `docker-java` do Anexo 2;
-   [ ] compreendo como Redis e Docker serão usados pelo `RunServer`.

------------------------------------------------------------------------

# Respostas

## 1. Imagem Docker

Cada máquina que execute um `RunServer` deve ter previamente
`python:3.12-slim`. Isto evita ter de descarregar a imagem quando chega
um pedido e reduz a dependência da rede.

## 2. Portos Redis

Em `6000:6379`, `6000` é o porto no host e `6379` é o porto do Redis
dentro do container.

## 3. Hash Redis

Um pedido tem vários campos (`estado`, `servidor`, `exitCode`, `stdout`,
`stderr`, etc.). O hash permite agrupá-los sob `pedido:<id>` e
atualizá-los individualmente.

## 4. Expiração

O Anexo 1 define uma expiração de 24 horas para que resultados antigos
não permaneçam indefinidamente no Redis.

## 5. UUID

Cada pedido necessita de um identificador único que o cliente possa
utilizar posteriormente para consultar o resultado.

## 6. `RedisClient`

Os handlers gRPC são concorrentes. Um `Jedis` simples representa uma
única ligação e não é thread-safe. O `RedisClient` gere internamente um
*pool* de ligações e é thread-safe, pelo que uma única instância pode ser
partilhada por todos os handlers do `RunServer`. Nas versões recentes do
Jedis substitui `JedisPooled`, que foi descontinuada.

## 7. Volume `/work`

A opção `-v` torna uma diretoria do host acessível dentro do container
em `/work`. O container lê aí o programa/input e escreve aí os
resultados.

## 8. Rede e memória

`--network none` impede o código não confiável de aceder à rede.
`--memory 256m` limita a memória disponível para a execução.

## 9. Ficheiros de resultado

Como `/work` corresponde a uma diretoria do host, o `RunServer` pode ler
`out.txt`, `err.txt` e `exit.txt` depois de o processo terminar.

## 10. Estados

O enunciado define:

``` text
0     -> CONCLUIDO
124   -> TIMEOUT
outro -> ERRO
```

Assim, por exemplo, `1` e `137` são classificados como `ERRO`.

## 11. `finally`

Os containers devem ser removidos mesmo quando ocorre uma exceção. Um
`finally` garante que o cleanup é tentado também no caminho de erro.

## 12. Thread separada

O método que executa o container bloqueia até este terminar. O
identificador deve ser devolvido imediatamente ao cliente, pelo que a
execução deve prosseguir noutra thread.

## 13. Devolver o UUID primeiro

A submissão é assíncrona: o pedido é registado como `PENDENTE`, o
cliente recebe o identificador e poderá consultar posteriormente o
resultado.

## 14. Qualquer RunServer pode responder

O estado não fica apenas no servidor que executou o programa. É guardado
no Redis partilhado, pelo que qualquer `RunServer` pode consultar
`pedido:<id>`.

------------------------------------------------------------------------

| Parâmetro           | Valor              |
| ------------------- | ------------------ |
| Imagem              | `python:3.12-slim` |
| Redis               | `ServerRedis`      |
| Porto Redis no host | `6000`             |
| Key                 | `pedido:<id>`      |
| TTL                 | 24 h               |
| Cliente Redis       | `RedisClient`      |
| Diretoria           | `/work`            |
| Rede                | `none`             |
| Memória             | 256 MB             |
| Timeout             | 10 s               |
| `0`                 | `CONCLUIDO`        |
| `124`               | `TIMEOUT`          |
| restantes           | `ERRO`             |

> **Nota:** `ciclo.py` e os excertos Java correspondem ao código
> mostrado nos anexos. Para `soma.py`, `erro.py` e `memoria.py`, o PDF
> indica o comportamento e os resultados esperados, mas não apresenta o
> código completo; as versões deste laboratório servem apenas para
> reproduzir esses comportamentos.
