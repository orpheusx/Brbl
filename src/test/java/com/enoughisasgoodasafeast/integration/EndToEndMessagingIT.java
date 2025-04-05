package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.PlatformGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static com.enoughisasgoodasafeast.SharedConstants.BRBL_ENQUEUE_ENDPOINT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class EndToEndMessagingIT {

    private static final Logger LOG = LoggerFactory.getLogger(EndToEndMessagingIT.class);
    public static final String COMPOSE_FILE_UNDER_TEST = "./docker-compose-jvm.yaml";

    // Apparently marking the annotated member static signals the test harness to reuse it for all the @Test methods.
    // Per https://java.testcontainers.org/test_framework_integration/junit_5/
    // Obviously, parallel execution is not supported.

    private static final ComposeContainer brblContainers = new ComposeContainer(
            new File(COMPOSE_FILE_UNDER_TEST))
            .withLocalCompose(true) // faster apparently but requires docker compose to be installed on any machine running the test
            .withLogConsumer("rcvr", new Slf4jLogConsumer(LOG))
            .withLogConsumer("operator", new Slf4jLogConsumer(LOG))
            .withLogConsumer("sndr", new Slf4jLogConsumer(LOG));

    @BeforeAll
    static void startContainers() throws IOException {
        LOG.info("Starting the RabbitMQ and Burble containers from {}", COMPOSE_FILE_UNDER_TEST);
        brblContainers.start();
    }

    @AfterAll
    static void stopContainer() {
        LOG.info("Shutting down the RabbitMQ and Burble containers from {}", COMPOSE_FILE_UNDER_TEST);
        brblContainers.stop();
    }


    @Test
    public void testSendAndVerifyResponse() throws IOException {
        assertDoesNotThrow(() -> {
            final Optional<ContainerState> rcvrByServiceName = brblContainers.getContainerByServiceName("rcvr");
            if (rcvrByServiceName.isEmpty()) {
                throw new IllegalStateException("Hmm, didn't find a container with serviceName, rcvr");
            }
            ContainerState rcvrContainerState = rcvrByServiceName.get();
            String host = rcvrContainerState.getHost();
            int port = rcvrContainerState.getBoundPortNumbers().getFirst();
            // ContainerState also features getPortBindings(). For Rcvr this returns a string: e.g. "4242:4242/tcp"
            // ContainerState.getServicePort("rcvr", 4242), howeve, throws complaining "Could not get a port for 'rcvr'. \
            //      Testcontainers does not have an exposed port configured for 'rcvr'."

            String rcvrUrl = String.format("http://%s:%s%s", host, port, BRBL_ENQUEUE_ENDPOINT);
            LOG.info("Derived Rcvr endpoint: {}", rcvrUrl);
            final PlatformGateway gateway = new PlatformGateway(rcvrUrl);
            try {
                gateway.init();

                final String[] messages = {
                        "17817299468:1234:21 hello",
                        "17817299468:1234:22 hello",
                        "17817299468:1234:23 hello",
                        "17817299468:1234:24 hello",
                        "17817299468:1234:25 hello",
                        "17817299468:1234:26 hello",
                        "17817299468:1234:27 hello",
                        "17817299468:1234:28 hello",
                        "17817299468:1234:29 hello",
                        "17817299468:1234:30 hello",
                };

                for (String message : messages) {
                    gateway.sendMoTraffic(message);
                }

                await().atMost(5, SECONDS).until(
                        mtResponsesDelivered(gateway.recordingHandler, messages.length)
                );

                // Look for log messages like "OperatorConsumer - Processed message: ..."
                for (String m : gateway.recordingHandler.retrieve()) {
                    LOG.info("recorded: {}", m);
                }

                String[] recordedMessages = gateway.recordingHandler.retrieve().toArray(new String[0]);

                // We expect the order of MOs and their response MTs to be the same.
                // As well, the content of the MTs should
                for (int i = 0; i < messages.length; i++) {
                    // e.g. "17817299468:1234:21 hello"
                    String[] inputMO = messages[i].split(":", 3);
                    String fromMO = inputMO[0];
                    String toMO = inputMO[1];
                    String textMO = inputMO[2];

                    // e.g. "Message[id=8829f0f0-9f67-4247-b4c5-72e8fef9813e, received=2025-04-03T22:40:51.754433625Z, type=MT, platform=BRBL, \
                    // from=1234, to=17817299468, text=PrintWithPrefix: 21 hello]"
                    String[] outputMT = recordedMessages[i].split(", ", 7);
                    String fromMT = outputMT[4].split("=")[1];
                    String toMT = outputMT[5].split("=")[1];
                    String textMT = outputMT[6].split("=")[1];

                    assertEquals(fromMO, toMT);
                    assertEquals(toMO, fromMT);
                    assertTrue(textMT.contains(textMO));
                }
            } finally {
                gateway.stop();
            }
        });
    }

    private Callable<Boolean> mtResponsesDelivered(PlatformGateway.RecordingHandler recordingHandler, int expectedCount) {
        return () -> {
            final List<String> recordedMessages = recordingHandler.retrieve();
            LOG.info("Waiting for {} messages...current count={}", expectedCount, recordedMessages.size());
            return !recordedMessages.isEmpty() && recordedMessages.size() >= expectedCount;
        };
    }

}