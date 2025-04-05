package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.GatewaySimStrategy;
import com.enoughisasgoodasafeast.PlatformGateway;
import com.enoughisasgoodasafeast.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
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

import static com.enoughisasgoodasafeast.operator.Functions.waitSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
public class ServiceAvailabilityIT {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceAvailabilityIT.class);
    private static final String BURBLE_CONTAINER = "burble-jvm:0.1.0";

    @Container
    final static RabbitMQContainer brokerContainer = new RabbitMQContainer("rabbitmq:4.0-management");
    // NOTE: RabbitMQContainer is still INCUBATING according to https://java.testcontainers.org/modules/rabbitmq/

//    final GenericContainer<SELF> rcvrContainer = new GenericContainer<>(BURBLE_CONTAINER);

//    @BeforeAll
//    static void startContainers() {
//
//    }

    @Test
    public void testContainerStarts() {
        LOG.info("Broker running on {}:{}",
                brokerContainer.getHost(),
                brokerContainer.getAmqpPort());
        assertEquals("localhost", brokerContainer.getHost());
    }
//    @Test
    public void testBasicMessageSend() throws IOException {
        /*final*/GenericContainer<?> rcvrContainer = null;
        /*final*/GenericContainer<?> fkopContainer = null;
        /*final*/GenericContainer<?> sndrContainer = null;
        try {
            // -------------------------------------- Setup Rabbit broker ----------------------------------------------//
            demandStart(brokerContainer);
            Map<String, String> envOverrides = getOverridesForBroker(brokerContainer);

            // -------------------------------------- Setup RCVR -----------------------------------------------------//
            final int rcvrExposedPort = Integer.parseInt(ConfigLoader.readConfig("rcvr.properties"/*"operator_test.properties"*/).getProperty("webserver.listener.port"));
            rcvrContainer = new GenericContainer<>(BURBLE_CONTAINER)
                    .withCommand("Rcvr")
                    .withExposedPorts(rcvrExposedPort)
                    .withEnv(envOverrides);
            demandStart(rcvrContainer);

            // -------------------------------------- Setup FKOP -----------------------------------------------------//
            fkopContainer = new GenericContainer<>(BURBLE_CONTAINER)
                    .withCommand("FakeOperator")
                    .withEnv(envOverrides);
            demandStart(fkopContainer);

            // -------------------------------------- Setup SNDR -----------------------------------------------------//
            sndrContainer = new GenericContainer<>(BURBLE_CONTAINER)
                    .withCommand("Sndr")
                    .withEnv(envOverrides);
            demandStart(sndrContainer);

            // -------------------------------------- Push traffic ---------------------------------------------------//
            final String effectiveRcvrUrl = String.format("http://%s:%d",
                    rcvrContainer.getHost(), rcvrContainer.getMappedPort(rcvrExposedPort));
            final PlatformGateway gateway = new PlatformGateway(effectiveRcvrUrl);

            gateway.init();
            sendMessagesAndEvaluateResults(gateway);
            gateway.stop();
        } finally {
            // Kind of a hack. Could we use the try-with-resource with the full set of containers to
            // insure a shutdown?
            if (rcvrContainer != null) rcvrContainer.stop();
            if (fkopContainer != null) fkopContainer.stop();
            if (sndrContainer != null) sndrContainer.stop();

            brokerContainer.stop();
        }

    }

//    @Test
    public void testRcvrReconnect() throws IOException {
        //-------------------------- Setup Broker -----------------------------------------------------//
        demandStart(brokerContainer);
        Map<String, String> envOverrides = getOverridesForBroker(brokerContainer);

        //-------------------------- Setup RCVR -----------------------------------------------------//
        final int rcvrExposedPort = Integer.parseInt(ConfigLoader.readConfig("rcvr.properties"/*"operator_test.properties"*/).getProperty("webserver.listener.port"));
        final GenericContainer<?> rcvrContainer = new GenericContainer<>(BURBLE_CONTAINER)
                .withCommand("Rcvr")
                .withExposedPorts(rcvrExposedPort)
                .withEnv(envOverrides);
        demandStart(rcvrContainer);

        final String rcvrHost = rcvrContainer.getHost();

        //-------------------------- Send first batch to RCVR -----------------------------------------------------//
        // Don't start the other components yet but starting sending input to the Rcvr.
        String effectiveRcvrUrl = String.format("http://%s:%d", rcvrHost, rcvrContainer.getMappedPort(rcvrExposedPort));
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

        effectiveRcvrUrl = String.format("http://%s:%d", rcvrHost, rcvrContainer.getMappedPort(rcvrExposedPort));
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
        final GenericContainer<?> fkopContainer = new GenericContainer<>(BURBLE_CONTAINER)
                .withCommand("FakeOperator")
                .withEnv(envOverrides);
        demandStart(fkopContainer);

        // -------------------------------------- Setup SNDR -----------------------------------------------------//
        final GenericContainer<?> sndrContainer = new GenericContainer<>(BURBLE_CONTAINER)
                .withCommand("Sndr")
                .withEnv(envOverrides);
        demandStart(sndrContainer);

        // -------------------------------------- Evaluate results -----------------------------------------------------//
        int resultCheckAttempts = 0;

        final String[] sentMessages = Stream.concat(Arrays.stream(messages1), Arrays.stream(messages2))
                .toArray(String[]::new);

        // Poll for expected number of responses
        List<String> responses = gateway.recordingHandler.retrieve();
        while ((responses.size() < sentMessages.length) && (++resultCheckAttempts < 5)) { // pretty generous latency
            waitSeconds(1);
            responses = gateway.recordingHandler.retrieve();
            LOG.info("Num messages received by recordingHandler: {}", responses.size());
        }
        LOG.debug("Received messages: {}", String.join(" | ", responses));

        assertEquals(sentMessages.length, responses.size(),
                "Received count doesn't match the number sent. Checked " + resultCheckAttempts + " times.");

        // Check the ordering and performed transformation
        evaluateSentAndReceived(sentMessages, responses);

        // Release the webserver port
        gateway.stop();
    }

//    public void testSndrReconnect() {
//
//    }

//    @Test
    public void testGatewayUnavailable() throws IOException {
        //-------------------------- Setup Broker -----------------------------------------------------//
        demandStart(brokerContainer);
        Map<String, String> envOverrides = getOverridesForBroker(brokerContainer);

        //-------------------------- Setup RCVR -----------------------------------------------------//
        final int rcvrExposedPort = Integer.parseInt(ConfigLoader.readConfig("rcvr.properties"/*"operator_test.properties"*/).getProperty("webserver.listener.port"));
        final GenericContainer<?> rcvrContainer = new GenericContainer<>(BURBLE_CONTAINER)
                .withCommand("Rcvr")
                .withExposedPorts(rcvrExposedPort)
                .withEnv(envOverrides);
        demandStart(rcvrContainer);

        // -------------------------------------- Setup FKOP -----------------------------------------------------//
        final GenericContainer<?> fkopContainer = new GenericContainer<>(BURBLE_CONTAINER)
                .withCommand("FakeOperator")
                .withEnv(envOverrides);
        demandStart(fkopContainer);

        // -------------------------------------- Setup SNDR -----------------------------------------------------//
        final GenericContainer<?> sndrContainer = new GenericContainer<>(BURBLE_CONTAINER)
                .withCommand("Sndr")
                .withEnv(envOverrides);
        demandStart(sndrContainer);

        //-------------------------- Send first batch -----------------------------------------------------//
        final String rcvrHost = rcvrContainer.getHost(); // FIXME usually returns "localhost"; use InetAdress instead?
        String effectiveRcvrUrl = String.format("http://%s:%d", rcvrHost, rcvrContainer.getMappedPort(rcvrExposedPort));
        PlatformGateway gateway = new PlatformGateway(effectiveRcvrUrl, new GatewaySimStrategy(1));
        gateway.init();

        final String[] messages1 = {
                "21 hello", "22 hello", "23 hello", "24 hello", "25 hello"
        };
        for (String message : messages1) {
            gateway.sendMoTraffic(message);
        }

        // -------------------------------------- Evaluate results -----------------------------------------------------//
        // Check the ordering and performed transformation
        evaluateSentAndReceived(gateway, messages1);

        // Clear the gateway recordings
        gateway.resetHistory();

        // Make the gateway unavailable
        gateway.filterTrafficByStrategy(true);

        final String[] messages2 = {
                "26 hello", "27 hello", "28 hello", "29 hello", "30 hello"
        };
        for (String message : messages2) {
            gateway.sendMoTraffic(message);
        } // We expect some errors in the Sndr log output, but hopefully it will recover.

        waitSeconds(3); // this is kind of smelly

        evaluateSentAndReceived(gateway, messages2);

        // Release the webserver port
        gateway.stop();

        // --------------- Same test with a slightly different strategy (2nd message fails)
        gateway = new PlatformGateway(effectiveRcvrUrl, new GatewaySimStrategy(3));
        gateway.init();
        gateway.filterTrafficByStrategy(true); // This is rather unwieldy...
        final String[] messages3 = {
                "31 hello", "32 hello", "33 hello", "34 hello", "35 hello"
        };
        // resend
        for (String message : messages3) {
            gateway.sendMoTraffic(message);
        }
        // Check the ordering and performed transformation
        evaluateSentAndReceived(gateway, messages3);

        // Release the webserver port
        gateway.stop();

        // --------------- Same test with a slightly different strategy (5th and final message fails)
        gateway = new PlatformGateway(effectiveRcvrUrl, new GatewaySimStrategy(5));
        gateway.init();
        gateway.filterTrafficByStrategy(true); // This is rather unwieldy...
        final String[] messages4 = {
                "36 hello", "37 hello", "38 hello", "39 hello", "40 hello"
        };
        // resend
        for (String message : messages4) {
            gateway.sendMoTraffic(message);
        }
        // Check the ordering and performed transformation
        evaluateSentAndReceived(gateway, messages4);

        // Release the webserver port
        gateway.stop();
    }

    private void sendMessagesAndEvaluateResults(PlatformGateway gateway) {
        final String[] messages = {
                "21 hello", "22 hello", "23 hello", "24 hello", "25 hello",
                "26 hello", "27 hello", "28 hello", "29 hello", "30 hello",
        };

        for (String message : messages) {
            gateway.sendMoTraffic(message);
        }

        // TODO DRY-ify this block, moving it into evaluateSentAndReceived()
        int resultCheckAttempts = 0;
        List<String> responses = gateway.recordingHandler.retrieve();
        while ((responses.size() < messages.length) && (++resultCheckAttempts < 5)) { // pretty generous latency
            LOG.info("Messages received by recordingHandler: {}", responses.size());
            waitSeconds(1);
            responses = gateway.recordingHandler.retrieve();
        }
        assertEquals(messages.length, responses.size(),
                "Received count doesn't match the number sent. Checked " + resultCheckAttempts + " times.");

        // Check the ordering and performed transformation
        evaluateSentAndReceived(messages, responses);
    }

    private void evaluateSentAndReceived(PlatformGateway gateway, String[] sentMessages) {
        // Poll for expected number of responses
        int resultCheckAttempts = 0;
        List<String> responses = gateway.recordingHandler.retrieve();

        while ((responses.size() < sentMessages.length) && (++resultCheckAttempts < 10)) { // pretty generous latency
            waitSeconds(1);
            responses = gateway.recordingHandler.retrieve();
            LOG.info("Number of messages received by recordingHandler: {}", responses.size());
        }

        LOG.info("Messages received: {}", responses.toString()); // FIXME change to debug
        assertEquals(sentMessages.length, responses.size(),
                "Received count doesn't match the number sent. Checked " + resultCheckAttempts + " times.");

        evaluateSentAndReceived(sentMessages, responses);

        LOG.info("Delivered messages: {}", responses.toString());
    }

    // TODO move this code into the above version and update callers
    private void evaluateSentAndReceived(String[] sentMessages, List<String> receivedMessages) {
        for (int i = 0; i < sentMessages.length; i++) {
            String[] sentParts = sentMessages[i].split(SharedConstants.TEST_SPACE_TOKEN, 2);
            String[] rcvdParts = receivedMessages.get(i).split(SharedConstants.TEST_SPACE_TOKEN, 2);

            LOG.debug("Comparing for index {}: sent={} rcvd={}", i, sentParts[0], rcvdParts[0]);
            assertEquals(sentParts[0], rcvdParts[0], "Unexpected ordering of messages");
            if (sentParts[1].equals("hello")) {
                assertEquals("goodbye", rcvdParts[1]);
            } else {
                fail("Unexpected message text.");
            }
        }
    }

    private void demandStart(GenericContainer<?> container) {
        String roleName = String.join(" ", container.getCommandParts());
        demandStart(container, roleName);
    }

    private void demandStart(RabbitMQContainer rabbitMQContainer) {
        demandStart(rabbitMQContainer, "Broker");
    }

    private void demandStart(GenericContainer<?> container, String roleName) {
        if (!container.isRunning()) {
            LOG.info("Container '{}' not running...starting it", roleName);
            container.start();
            if (!container.isRunning()) {
                fail(roleName + " failed to start.");
            }
        } else {
            LOG.info("Container '{}' already running.", roleName);
        }

        Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(LOG);
        container.followOutput(logConsumer);
    }

    private Map<String, String> getOverridesForBroker(RabbitMQContainer brokerContainer) throws UnknownHostException {
        Map<String, String> envOverrides = new HashMap<>();
        envOverrides.put("producer.queue.port", String.valueOf(brokerContainer.getAmqpPort()));
        envOverrides.put("producer.queue.host", InetAddress.getLocalHost().getHostAddress());
        LOG.info("Config overrides: {}", envOverrides);
        return envOverrides;
    }

}
