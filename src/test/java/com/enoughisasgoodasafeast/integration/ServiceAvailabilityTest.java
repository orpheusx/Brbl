package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.PlatformGateway;
import com.enoughisasgoodasafeast.SharedConstants;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.enoughisasgoodasafeast.SharedConstants.STANDARD_RABBITMQ_PORT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
public class ServiceAvailabilityTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceAvailabilityTest.class);

    @Container
    /*private static*/final RabbitMQContainer brokerContainer = new RabbitMQContainer("rabbitmq:4.0-management");

    // This class is still INCUBATING according to https://java.testcontainers.org/modules/rabbitmq/


    // TODO Subclass GenericContainer for each our different containerized applications
//    @Container
//    private static final GenericContainer<?> rcvrContainer = new GenericContainer<>("burble-jvm:0.1.0")
//            .withCommand("Rcvr")
//            .withExposedPorts(4242) // requires bash to be installed in the running container image. Busybox doesn't do this!
//            .withEnv("");


//    @Container
//    private static final GenericContainer<?> fkopContainer = new GenericContainer<>("burble-jvm:0.1.0")
//            .withCommand("FakeOperator")
//            .waitingFor(new LogMessageWaitStrategy()
//                    .withRegEx(".*consumerTag returned from basicConsume.*\\s")
//                    //.withTimes(2)
//                    .withStartupTimeout(Duration.of(5, ChronoUnit.SECONDS)));
    ;
//
//    @Container
//    private static final GenericContainer<?> sndrContainer = new GenericContainer<>("burble-jvm:0.1.0")
//            .withCommand("Sndr")
//            .waitingFor(new LogMessageWaitStrategy()
//                    .withRegEx(".*consumerTag returned from basicConsume.*\\s")
//                    //.withTimes(2)
//                    .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS)));


    @Test
    public void testRcvrReconnect() throws IOException {
        demandStart(brokerContainer);
        if (!brokerContainer.isRunning()) {
            LOG.error("Broker container still isn't running. Testcontainers sucks.");
        } else {
            LOG.info("Thank christ, the fucking broker container started.");
        }
        final Integer brokerPort =/* 5672;*/brokerContainer.getMappedPort(Integer.parseInt(STANDARD_RABBITMQ_PORT));
        LOG.info("broker running on {}:{}", brokerContainer.getHost(), brokerPort);

        Map<String, String> envOverrides = new HashMap<>();
        envOverrides.put("queue.port", String.valueOf(brokerPort));

        final GenericContainer<?> rcvrContainer = new GenericContainer<>("burble-jvm:0.1.0")
                .withCommand("Rcvr")
                .withExposedPorts(4242) // requires bash to be installed in the running container image. Busybox doesn't do this!
                .withEnv(envOverrides);

        demandStart(rcvrContainer);

        Slf4jLogConsumer rcvrLogConsumer = new Slf4jLogConsumer(LOG);
        rcvrContainer.followOutput(rcvrLogConsumer);

        Integer rcvrPort = rcvrContainer.getMappedPort(4242);
        String rcvrHost = rcvrContainer.getHost();

        LOG.info("Rcvr host and port: {} {}", rcvrHost, rcvrPort);


        if (!rcvrContainer.isRunning()) {
            LOG.error("Rcvr container still isn't running. Testcontainers sucks.");
        } else {
            LOG.info("Thank christ, the fucking rcvr container started.");
        }

        final GenericContainer<?> fkopContainer = new GenericContainer<>("burble-jvm:0.1.0")
                .withCommand("FakeOperator")
                .withEnv(envOverrides);

        demandStart(fkopContainer);

        Slf4jLogConsumer fkopLogConsumer = new Slf4jLogConsumer(LOG);
        fkopContainer.followOutput(fkopLogConsumer);

        if (!fkopContainer.isRunning()) {
            LOG.error("Fkop container still isn't running. Testcontainers sucks.");
        } else {
            LOG.info("Thank christ, the fucking Fkop container started.");
        }

        final GenericContainer<?> sndrContainer = new GenericContainer<>("burble-jvm:0.1.0")
                .withCommand("Sndr")
                .withEnv(envOverrides);

        demandStart(sndrContainer);

        Slf4jLogConsumer sndrLogConsumer = new Slf4jLogConsumer(LOG).withMdc("SNDR  ","SNDR");
        sndrContainer.followOutput(sndrLogConsumer);

        if (!sndrContainer.isRunning()) {
            LOG.error("Sndr container still isn't running. Testcontainers sucks.");
        } else {
            LOG.info("Thank christ, the fucking Sndr container started.");
        }


        final String effectiveRcvrUrl = String.format("http://%s:%d", /*rcvrHost*/"192.168.1.155", rcvrPort);
        final PlatformGateway gateway = new PlatformGateway(effectiveRcvrUrl);
        gateway.init();

        sendMessages(gateway);
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

    private void  demandStart(GenericContainer container) {
        if (!container.isRunning()) {
            LOG.info("Container {} not running...starting it", container.getClass());
            container.start();
        } else {
            LOG.info("Container already running.");
        }
    }

    private void waitSeconds(int num) {
        try {
            LOG.info("waiting {} secs", num);
            Thread.sleep(Duration.ofSeconds(num));
        } catch (InterruptedException e) {
            LOG.error("waitSeconds was interrupted", e);
        }
    }

}
