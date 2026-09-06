package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager;
import com.enoughisasgoodasafeast.operator.TestingPersistenceManager;
import com.rabbitmq.client.ShutdownSignalException;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.integration.IntegrationTestFunctions.loadPropertiesWithContainerOverrides;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
public class SndrMessageFlowIT {

    private static final Logger LOG = LoggerFactory.getLogger(SndrMessageFlowIT.class);

    private static final RabbitMQContainer brokerContainer = new RabbitMQContainer(
            "rabbitmq:4.3-management-alpine");
    private static Properties testProps;

    private QueueProducer opr8rSurrogate;
    private TestingPersistenceManager persistenceManager;
    private Sndr sndr;
    private DLQLogger dlqLogger; // Drains the queue of messages that failed to send.

    @BeforeAll
    static void startBrokerForAllTests() throws IOException {
        brokerContainer.start();
        testProps = loadPropertiesWithContainerOverrides(brokerContainer, "sndr_message_flow_it.properties");
    }

    @AfterAll
    static void stopContainer() {
        brokerContainer.stop();
    }

    @BeforeEach
    void setUp() throws IOException, TimeoutException, PersistenceManager.PersistenceManagerException {
        opr8rSurrogate = RabbitQueueProducer.createQueueProducer(testProps); // Sends output MTs to the queue Sndr consumes
        persistenceManager = new TestingPersistenceManager();
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


    //@Test
    void sendMessageToTelnyx() {
        // Verify we're pointing to the right endpoint; we only send never receive.
        // 1) create a Message
        final var message1 = new Message(MessageType.MT, "+17814567890", "+17817209452", "test message1");
        final var message2 = new Message(MessageType.MT, "+17814567890", "+17817209452", "test message2");
        final var message3 = new Message(MessageType.MT, "+17814567890", "+17817209452", "test message3");
        // 2) put it on the MT queue. Use the Confab client to
        sndr.process(message1);
        sndr.process(message2);
        sndr.process(message3);
//        if (statusException.isSuccess()) {
//            LOG.info("Message sent successfully");
//        } else {
//            LOG.error("Failed to send message: {}", statusException);
//        }
        // 3) wait for it to appear on the DLQ (if message is expected to fail.)

    }
}
