package com.example.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.nio.charset.StandardCharsets;

public class DockerDemo {

    private static final String IMAGE = "busybox";
    private static final String TAG = "latest";

    public static void main(String[] args) throws Exception {
        // Local daemon: DOCKER_HOST or the platform default (Unix socket / Windows named pipe)
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        ApacheDockerHttpClient http = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();

        try (DockerClient docker = DockerClientImpl.getInstance(config, http)) {
            pullIfMissing(docker, IMAGE, TAG);

            String id = docker.createContainerCmd(IMAGE + ":" + TAG)
                    .withCmd("sh", "-c", "echo Hello from a container; exit 3")
                    .exec()
                    .getId();
            docker.startContainerCmd(id).exec();

            // Blocks until the container exits
            int exitCode = docker.waitContainerCmd(id)
                    .exec(new WaitContainerResultCallback())
                    .awaitStatusCode();

            // Container stdout and stderr
            docker.logContainerCmd(id)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            System.out.print("[container] "
                                    + new String(frame.getPayload(), StandardCharsets.UTF_8));
                        }
                    })
                    .awaitCompletion();

            System.out.println("Exit code: " + exitCode);

            docker.removeContainerCmd(id).exec();
        }
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
