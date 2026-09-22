package com.example;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class GrpcServer {

    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder
                .forPort(50051)
                .addService(new GreetingServiceImpl())
                .build();

        server.start();

        System.out.println("gRPC server started on port 50051");

        server.awaitTermination();
    }
}
