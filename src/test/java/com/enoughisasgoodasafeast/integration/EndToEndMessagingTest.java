package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.PlatformGateway;
import com.enoughisasgoodasafeast.SharedConstants;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
public class EndToEndMessagingTest {

    private static final Logger LOG = LoggerFactory.getLogger(EndToEndMessagingTest.class);
    public static final String COMPOSE_FILE_UNDER_TEST = "./docker-compose-jvm.yaml";

    // Apparently marking the annotated member static signals the test harness to reuse it for all the @Test methods.
    // Per https://java.testcontainers.org/test_framework_integration/junit_5/
    // Obviously, parallel execution is not supported.

    @Container
    private static final ComposeContainer brblContainers = new ComposeContainer(
            new File(COMPOSE_FILE_UNDER_TEST))
            .withLocalCompose(true);
            // NOTE:
            // LocalCompose mode is faster apparently but requires docker compose to be installed on any machine running the test

    static {
        LOG.info("Starting the Burble containers from {}", COMPOSE_FILE_UNDER_TEST);
        brblContainers.start(); // the @Container annotation isn't starting the container so we have to call start() manually
    }

    @Test
    public void testSendAndVerifyResponse() throws IOException {

        final PlatformGateway gateway = new PlatformGateway();
        gateway.init();

        final String[] messages = {
            "21 hello",
            "22 hello",
            "23 hello",
            "24 hello",
            "25 hello",
            "26 hello",
            "27 hello",
            "28 hello",
            "29 hello",
            "30 hello",

        };

        for (String message : messages) {
            gateway.sendMoTraffic(message);
        }

        waitSeconds(3); // FIXME alternative way to wait for the results to arrive?

        final List<String> responses = gateway.recordingHandler.retrieve();
        assertEquals(messages.length, responses.size(), "Received count doesn't match the number sent.");

        // check the ordering and performed transformation
        for (int i = 0; i < messages.length; i++) {
            String[] sentParts = messages[i].split(SharedConstants.TEST_SPACE_TOKEN,2); // FIXME make the space a shared constant
            String[] rcvdParts = responses.get(i).split(SharedConstants.TEST_SPACE_TOKEN,2);

            assertEquals(sentParts[0], rcvdParts[0]);
            if (sentParts[1].equals("hello")) {
                assertEquals("goodbye", rcvdParts[1]);
            }
        }

    }

    private void waitSeconds(int num) {
        try {
            Thread.sleep(Duration.ofSeconds(num));
        } catch (InterruptedException e) {
            LOG.error("waitSeconds was interrupted", e);
        }
    }

}