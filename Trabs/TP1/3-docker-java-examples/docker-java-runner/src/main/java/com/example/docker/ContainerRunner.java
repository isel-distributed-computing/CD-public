package com.example.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Equivalent to:
 * docker run --name run-&lt;id&gt; --network none --memory 256m --memory-swap 256m --user &lt;uid&gt;:&lt;gid&gt; \
 *   -v &lt;dir&gt;:/work python:3.12-slim \
 *   sh -c "timeout 10 python /work/prog.py &lt; /work/input.txt &gt; /work/out.txt 2&gt; /work/err.txt; echo \$? &gt; /work/exit.txt"
 */
public class ContainerRunner {

    private static final String IMAGE = "python";
    private static final String TAG = "3.12-slim";
    private static final long MEMORY = 256L * 1024 * 1024;
    private static final String COMMAND =
            "timeout 10 python /work/prog.py < /work/input.txt > /work/out.txt 2> /work/err.txt;"
            + " echo $? > /work/exit.txt";

    public static void main(String[] args) throws Exception {
        // Request directory with prog.py and input.txt
        Path dir = Path.of(args.length > 0 ? args[0] : "request").toAbsolutePath().normalize();

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        ApacheDockerHttpClient http = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();

        try (DockerClient docker = DockerClientImpl.getInstance(config, http)) {
            pullIfMissing(docker, IMAGE, TAG);

            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withNetworkMode("none")
                    .withMemory(MEMORY)
                    .withMemorySwap(MEMORY)
                    .withBinds(new Bind(dir.toString(), new Volume("/work")));

            CreateContainerCmd create = docker.createContainerCmd(IMAGE + ":" + TAG)
                    .withName("run-" + System.currentTimeMillis())
                    .withHostConfig(hostConfig)
                    .withCmd("sh", "-c", COMMAND);

            String user = currentUser();
            if (user != null) {
                create.withUser(user);
            }

            String id = create.exec().getId();
            try {
                docker.startContainerCmd(id).exec();
                // sh always ends with "echo", so the real exit code is in exit.txt
                // next call will block the calling tread until the containers exits
                docker.waitContainerCmd(id).exec(new WaitContainerResultCallback()).awaitStatusCode();
            } finally {
                docker.removeContainerCmd(id).exec();
            }
        }

        int exitCode = readExitCode(dir.resolve("exit.txt"));
        String state = switch (exitCode) {
            case 0 -> "CONCLUIDO";
            case 124 -> "TIMEOUT";
            default -> "ERRO";
        };

        System.out.println("state: " + state + " (exit " + exitCode + ")");
        System.out.println("--- stdout ---");
        System.out.print(Files.readString(dir.resolve("out.txt")));
        System.out.println("--- stderr ---");
        System.out.print(Files.readString(dir.resolve("err.txt")));
    }

    // uid:gid of the current user (Linux/macOS); null on Windows
    private static String currentUser() {
        if (System.getProperty("os.name").startsWith("Windows")) {
            return null;
        }
        com.sun.security.auth.module.UnixSystem unix = new com.sun.security.auth.module.UnixSystem();
        return unix.getUid() + ":" + unix.getGid();
    }

    private static int readExitCode(Path file) throws IOException {
        return Files.exists(file) ? Integer.parseInt(Files.readString(file).strip()) : -1;
    }

    private static void pullIfMissing(DockerClient docker, String image, String tag)
            throws InterruptedException {
        try {
            docker.inspectImageCmd(image + ":" + tag).exec();
        } catch (NotFoundException e) {
            System.out.println("Pulling " + image + ":" + tag + " ...");
            docker.pullImageCmd(image).withTag(tag)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();
        }
    }
}
