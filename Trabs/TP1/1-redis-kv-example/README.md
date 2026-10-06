# Redis — micro exemplo chave/valor em Java (Gradle)

Guarda e lê pares chave/valor num servidor Redis com o cliente
[Jedis](https://github.com/redis/jedis) 8.0.1. Gradle 9.7.1 (wrapper incluído), Java 21.

## 1. Lançar o servidor Redis (à parte)

Requer Docker (em Windows, Docker Desktop).

```bash
./start-redis.sh                        # Linux/macOS  (Windows: start-redis.bat)
docker exec -it redis-dev redis-cli     # consola: KEYS *, GET aluno:1234:nome, TTL sessao:abc
docker stop redis-dev                   # parar (o container é removido)
```

O porto fica publicado apenas em `127.0.0.1`: por omissão o Redis não tem autenticação,
pelo que não deve ficar acessível a partir da rede.

Sem Docker, com o Redis instalado localmente (`apt install redis-server`, `brew install redis`),
basta `redis-server` num terminal à parte.

## 2. Executar o exemplo

```bash
./gradlew run                          # localhost 6379
./gradlew run --args="10.0.0.5 6379"   # outro servidor
```

Saída esperada:

```
GET aluno:1234:nome  -> Maria
GET aluno:9999:nome  -> null
TTL sessao:abc       -> 30 s
EXISTS aluno:1234:nome -> false
```

## O que o exemplo mostra

| Operação Java                                   | Comando Redis               |
|-------------------------------------------------|-----------------------------|
| `redis.set(k, v)`                               | `SET k v`                   |
| `redis.get(k)` (devolve `null` se não existir)  | `GET k`                     |
| `redis.set(k, v, SetParams.setParams().ex(30))` | `SET k v EX 30`             |
| `redis.ttl(k)`                                  | `TTL k`                     |
| `redis.del(k)` / `redis.exists(k)`              | `DEL k` / `EXISTS k`        |

`RedisClient` (a API recomendada desde o Jedis 7) mantém um pool de ligações e é
*thread-safe*: numa aplicação cria-se uma instância e partilha-se entre threads.
O `SETEX` está *deprecated* no Jedis desde a 7.3, daí o uso de `SET ... EX`.
