package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.PlatformGateway;
import com.enoughisasgoodasafeast.SharedConstants;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.enoughisasgoodasafeast.SharedConstants.STANDARD_RABBITMQ_PORT;
import static com.enoughisasgoodasafeast.util.Functions.waitSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
public class ServiceAvailabilityTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceAvailabilityTest.class);

    @Container
    final RabbitMQContainer brokerContainer = new RabbitMQContainer("rabbitmq:4.0-management");
    // NOTE: RabbitMQContainer is still INCUBATING according to https://java.testcontainers.org/modules/rabbitmq/

    //@Test
    public void testBasicMessageSend() throws IOException {

        // -------------------------------------- Setup Rabbit broker ----------------------------------------------//
        demandStart(brokerContainer);
        if (!brokerContainer.isRunning()) {
            LOG.error("Broker container still isn't running. Testcontainers sucks.");
        } else {
            LOG.info("Thank christ, the fucking broker container started.");
        }

        final Integer brokerPort =/* 5672;*/brokerContainer.getMappedPort(Integer.parseInt(STANDARD_RABBITMQ_PORT));
        final String brokerHost = InetAddress.getLocalHost().getHostAddress();//brokerContainer.getHost();
        LOG.info("Broker running on {}:{}", brokerHost, brokerPort);

        // Override base value for these properties with the Testcontainer lib's randomized port.
        Map<String, String> envOverrides = new HashMap<>();
        envOverrides.put("queue.port", String.valueOf(brokerPort));
        envOverrides.put("queue.host", brokerHost);

        // -------------------------------------- Setup RCVR -----------------------------------------------------//
        final GenericContainer<?> rcvrContainer = new GenericContainer<>("burble-jvm:0.1.0")
                .withCommand("Rcvr")
                .withExposedPorts(4242) // requires bash to be installed in the running container image but Busybox doesn't include bash!
                .withEnv(envOverrides);

        demandStart(rcvrContainer);

        Integer rcvrPort = rcvrContainer.getMappedPort(4242);
        String rcvrHost = rcvrContainer.getHost();

        LOG.info("Rcvr host and port: {} {}", rcvrHost, rcvrPort);

        if (!rcvrContainer.isRunning()) {
            LOG.error("Rcvr container still isn't running. Testcontainers sucks.");
        } else {
            LOG.info("Thank christ, the fucking rcvr container started.");
        }


        // -------------------------------------- Setup FKOP -----------------------------------------------------//
        final GenericContainer<?> fkopContainer = new GenericContainer<>("burble-jvm:0.1.0")
                .withCommand("FakeOperator")
                .withEnv(envOverrides);

        demandStart(fkopContainer);

        if (!fkopContainer.isRunning()) {
            LOG.error("Fkop container still isn't running. Testcontainers sucks.");
        } else {
            LOG.info("Thank christ, the fucking Fkop container started.");
        }


        waitSeconds(4);
        // -------------------------------------- Setup SNDR -----------------------------------------------------//
        final GenericContainer<?> sndrContainer = new GenericContainer<>("burble-jvm:0.1.0")
                .withCommand("Sndr")
                .withEnv(envOverrides);

        demandStart(sndrContainer);

        if (!sndrContainer.isRunning()) {
            LOG.error("Sndr container still isn't running. Testcontainers sucks.");
        } else {
            LOG.info("Thank christ, the fucking Sndr container started.");
        }


        final String effectiveRcvrUrl = String.format("http://%s:%d", rcvrHost, rcvrPort);
        final PlatformGateway gateway = new PlatformGateway(effectiveRcvrUrl);
        gateway.init();
        sendMessages(gateway);
        gateway.stop();
    }

    @Test
    public void testRcvrReconnect() throws UnknownHostException {
        //-------------------------- Setup Broker -----------------------------------------------------//
        demandStart(brokerContainer);
        if (!brokerContainer.isRunning()) {
            fail("Broker failed to start.");
        }
        final Integer brokerPort = brokerContainer.getMappedPort(Integer.parseInt(STANDARD_RABBITMQ_PORT));
        final String brokerHost = InetAddress.getLocalHost().getHostAddress();
        LOG.info("Broker running on {}:{}", brokerHost, brokerPort);

        // Override base value for these properties with the Testcontainer lib's randomized port.
        Map<String, String> envOverrides = new HashMap<>();
        envOverrides.put("queue.port", String.valueOf(brokerPort));
        envOverrides.put("queue.host", brokerHost);

        //-------------------------- Setup RCVR -----------------------------------------------------//
        final GenericContainer<?> rcvrContainer = new GenericContainer<>("burble-jvm:0.1.0")
                .withCommand("Rcvr")
                .withExposedPorts(4242)
                .withEnv(envOverrides);
        demandStart(rcvrContainer);
        if (!rcvrContainer.isRunning()) {
            fail("Rcvr failed to start.");
        }

        Integer rcvrPort = rcvrContainer.getMappedPort(4242);
        String rcvrHost = rcvrContainer.getHost();
        LOG.info("Rcvr host and port: {} {}", rcvrHost, rcvrPort);


        //-------------------------- Send first batch to RCVR -----------------------------------------------------//
        // Don't start the other components yet but starting sending input to the Rcvr.
        String effectiveRcvrUrl = String.format("http://%s:%d", rcvrHost, rcvrPort);
        PlatformGateway gateway = new PlatformGateway(effectiveRcvrUrl);
        gateway.init();

        final String[] messages1 = {
                "21 hello",
                "22 hello",
                "23 hello",
                "24 hello",
                "25 hello"
        };

        for (String message : messages1) {
            gateway.sendMoTraffic(message);
        }

        gateway.stop();

        //-------------------------- Shutdown RCVR -----------------------------------------------------//
        rcvrContainer.stop();
        if (rcvrContainer.isRunning()) {
            fail("Rcvr failed to stop");
        }

        //-------------------------- Re-start RCVR -----------------------------------------------------//
        LOG.info("Okay Rcvr stopped...restarting it");
        demandStart(rcvrContainer);
        if(!rcvrContainer.isRunning()) {
            fail("Rcvr failed to start the second time.");
        }

        // The port for the new container may have changed
        rcvrPort = rcvrContainer.getMappedPort(4242);
        LOG.info("Rcvr host and port for second start: {} {}", rcvrHost, rcvrPort);

        effectiveRcvrUrl = String.format("http://%s:%d", rcvrHost, rcvrPort);
        gateway = new PlatformGateway(effectiveRcvrUrl);
        gateway.init();

        final String[] messages2 = {
                "26 hello",
                "27 hello",
                "28 hello",
                "29 hello",
                "30 hello"
        };

        for (String message : messages2) {
            gateway.sendMoTraffic(message);
        }

        // -------------------------------------- Setup FKOP -----------------------------------------------------//
        final GenericContainer<?> fkopContainer = new GenericContainer<>("burble-jvm:0.1.0")
                .withCommand("FakeOperator")
                .withEnv(envOverrides);
        demandStart(fkopContainer);
        if (!fkopContainer.isRunning()) {
            fail("Fkop didn't start.");
        }

        // -------------------------------------- Setup SNDR -----------------------------------------------------//
        final GenericContainer<?> sndrContainer = new GenericContainer<>("burble-jvm:0.1.0")
                .withCommand("Sndr")
                .withEnv(envOverrides);
        demandStart(sndrContainer);
        if (!sndrContainer.isRunning()) {
            fail("Sndr didn't start.");
        }

        // -------------------------------------- Evaluate results -----------------------------------------------------//

        int resultCheckAttempts = 0;

        final String[] sentMessages = Stream.concat(Arrays.stream(messages1), Arrays.stream(messages2))
                .toArray(String[]::new);

        List<String> responses = gateway.recordingHandler.retrieve();
        while ((responses.size() < sentMessages.length) && (++resultCheckAttempts < 5)) { // pretty generous latency
            LOG.info("Messages received by recordingHandler: {}", responses.size());
            waitSeconds(1);
            responses = gateway.recordingHandler.retrieve();
        }
        LOG.info("Messages: {}", String.join(" | ", responses));

        assertEquals(sentMessages.length, responses.size(),
                "Received count doesn't match the number sent. Checked " + resultCheckAttempts + " times.");

        // check the ordering and performed transformation
        for (int i = 0; i < sentMessages.length; i++) {
            String[] sentParts = sentMessages[i].split(SharedConstants.TEST_SPACE_TOKEN,2); // FIXME make the space a shared constant
            String[] rcvdParts = responses.get(i).split(SharedConstants.TEST_SPACE_TOKEN,2);

            assertEquals(sentParts[0], rcvdParts[0]);
            if (sentParts[1].equals("hello")) {
                assertEquals("goodbye", rcvdParts[1]);
            }
        }

    }


    // TODO refactor this into a util class that EndToEndMessagingTest also uses
    private void sendMessages(PlatformGateway gateway) {
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

        int resultCheckAttempts = 0;
        List<String> responses = gateway.recordingHandler.retrieve();
        while ((responses.size() < messages.length) && (++resultCheckAttempts < 5)) { // pretty generous latency
            LOG.info("Messages received by recordingHandler: {}", responses.size());
            waitSeconds(1);
            responses = gateway.recordingHandler.retrieve();
        }
        assertEquals(messages.length, responses.size(),
                "Received count doesn't match the number sent. Checked " + resultCheckAttempts + " times.");

        // check the ordering and performed transformation
        for (int i = 0; i < messages.length; i++) {
            String[] sentParts = messages[i].split(SharedConstants.TEST_SPACE_TOKEN,2); // FIXME make the space a shared constant
            String[] rcvdParts = responses.get(i).split(SharedConstants.TEST_SPACE_TOKEN,2);

            assertEquals(sentParts[0], rcvdParts[0]);
            if (sentParts[1].equals("hello")) {
                assertEquals("goodbye", rcvdParts[1]);
            }
        }

        LOG.info("Messages sent, received, and verified.");

    }

    private void  demandStart(GenericContainer<?> container) {
        if (!container.isRunning()) {
            LOG.info("Container {} not running...starting it", container.getClass());
            container.start();
        } else {
            LOG.info("Container already running.");
        }
        Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(LOG);
        container.followOutput(logConsumer);
        waitSeconds(2); // FIXME get the fuck out of here
    }

}
