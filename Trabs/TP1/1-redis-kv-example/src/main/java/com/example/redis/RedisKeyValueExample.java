package com.example.redis;

import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

public class RedisKeyValueExample {

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;

        // Thread-safe, pooled client: create once and share.
        try (RedisClient redis = RedisClient.builder().hostAndPort(host, port).build()) {

            // SET key value
            redis.set("aluno:1234:nome", "Maria");

            // GET key
            String nome = redis.get("aluno:1234:nome");
            System.out.println("GET aluno:1234:nome  -> " + nome);

            // Missing key returns null
            System.out.println("GET aluno:9999:nome  -> " + redis.get("aluno:9999:nome"));

            // SET key value EX seconds
            redis.set("sessao:abc", "ativa", SetParams.setParams().ex(30));
            System.out.println("TTL sessao:abc       -> " + redis.ttl("sessao:abc") + " s");

            // DEL key
            redis.del("aluno:1234:nome");
            System.out.println("EXISTS aluno:1234:nome -> " + redis.exists("aluno:1234:nome"));
        }
    }
}
