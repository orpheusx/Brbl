package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.datagen.KnownData;
import com.enoughisasgoodasafeast.operator.*;
import com.enoughisasgoodasafeast.sndr.ProcessStateMessage;
import com.enoughisasgoodasafeast.sndr.sim.server.TelnyxServerMain;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.integration.IntegrationTestFunctions.loadPropertiesWithContainerOverrides;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
public class SndrMessageFlowUsingPostgresIT {

    private static final Logger LOG = LoggerFactory.getLogger(SndrMessageFlowUsingPostgresIT.class);

    private static final RabbitMQContainer brokerContainer = new RabbitMQContainer(
            "rabbitmq:4.3-management-alpine");
    public static final String SUBSCRIBER = "+17817209452";

    static Properties testProps;
    static WebServer telnyxGateway;

    static final String ROUTE_CHANNEL = KnownData.knownRouteIdsAndChannels[0][1]; // "+17814567890";

    QueueProducer opr8rSurrogate;
    PersistenceManager persistenceManager;
    Sndr sndr;
    DLQLogger dlqLogger; // Drains the queue of messages that failed to send.

    @BeforeEach
    void setUp() throws IOException, TimeoutException, PersistenceManager.PersistenceManagerException {
        opr8rSurrogate = RabbitQueueProducer.createQueueProducer(testProps);
        PostgresPersistenceManager.createPersistenceManager(testProps);

        sndr = new Sndr(persistenceManager);
        sndr.init(testProps);

        try {
            dlqLogger = DLQLogger.createDLQLogger(testProps);
        } catch (Exception e) {
            fail("Failed to create DLQ logger:", e);
            return;
        }
    }

    @AfterEach
    void tearDown() {
        try {
            if (dlqLogger != null) dlqLogger.stopConsuming();
            LOG.info("DLQ consumer shut down");
            if(opr8rSurrogate!=null) opr8rSurrogate.shutdown();
            LOG.info("QueueProducer simulating Opr8r shut down");
            if(sndr!=null) sndr.shutdown();

        } catch (IOException | TimeoutException e) {
            LOG.warn(e.getMessage());
        }
    }

    @BeforeAll
    static void startServicesForAllTests() throws IOException {
        brokerContainer.start();
        testProps = loadPropertiesWithContainerOverrides(brokerContainer, "sndr_message_flow_it.properties");
        telnyxGateway = TelnyxServerMain.startServer();
    }

    @AfterAll
    static void stopContainer() {
        telnyxGateway.stop();
        brokerContainer.stop();
    }

    @Test
    void sendExpiredMessageViaBroker() {

        var expiringMessage = new Message(UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS), MessageType.MT, Platform.SMS,
                ROUTE_CHANNEL, SUBSCRIBER, "This is too old to send.");

        assertTrue(opr8rSurrogate.enqueue(expiringMessage));

        var expiredMessages = dlqLogger.getDeadMessages();
        // Expired messages should be sent to the DLQ.
        await().atMost(2, SECONDS).until(anyDeadMessages(expiredMessages));

        var expiredRecord = expiredMessages.getFirst();
        assertSame(ProcessState.EXPIRED, expiredRecord.processState());
        assertEquals(expiringMessage, expiredRecord.message());

    }

    private Callable<Boolean> anyDeadMessages(List<ProcessStateMessage> deadMessages) {
        return () -> !deadMessages.isEmpty();
    }

}
