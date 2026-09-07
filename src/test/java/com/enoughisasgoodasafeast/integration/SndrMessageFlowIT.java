package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager;
import com.enoughisasgoodasafeast.operator.ProcessState;
import com.enoughisasgoodasafeast.operator.TestingPersistenceManager;
import com.enoughisasgoodasafeast.sndr.sim.server.TelnyxMessageService;
import com.enoughisasgoodasafeast.sndr.sim.server.TelnyxServerMain;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.integration.IntegrationTestFunctions.loadPropertiesWithContainerOverrides;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class SndrMessageFlowIT {

    private static final Logger LOG = LoggerFactory.getLogger(SndrMessageFlowIT.class);

    private static final RabbitMQContainer brokerContainer = new RabbitMQContainer(
            "rabbitmq:4.3-management-alpine");
    private static Properties testProps;
    private static WebServer telnyxGateway;

    private QueueProducer opr8rSurrogate;
    private TestingPersistenceManager persistenceManager;
    private Sndr sndr;
    private DLQLogger dlqLogger; // Drains the queue of messages that failed to send.

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

    @Test
    void sendMessageToTelnyxBypassBroker() {
        var psk1 = sndr.process(
                new Message(MessageType.MT, "+17814567890", "+17817209452", "test message1")
        );
        assertSame(ProcessState.OK, psk1.processState());

        var psk2 = sndr.process(
                new Message(MessageType.MT, "+17814567890", "+17817209453", "test message2")
        );
        assertSame(ProcessState.OK, psk2.processState());

        var psk3 = sndr.process(
                new Message(MessageType.MT, "+17814567890", "+17817209454", "test message3")
        );
        assertSame(ProcessState.OK, psk3.processState());
    }

    @Test
    void sendMessageViaBroker() {
        final var message = new Message(MessageType.MT, "+17814567890", "+17817209452", "test message1");
        final boolean enqueued = opr8rSurrogate.enqueue(message);
        assertTrue(enqueued);

        // Wait to find out if the message was sent.
        final var sentMessages = TelnyxServerMain.getTelnyxMessageService().sentMessages;
        await().atMost(3, SECONDS).until(anyMtAccepted(sentMessages));

        boolean found = false;
        for (TelnyxMessageService.IdMessage idMessage : sentMessages) {
            var cmr = idMessage.createMessageRequest();
            found = cmr.getTo().equals(message.to()) &&
                    cmr.getFrom().equals(message.from()) &&
                    cmr.getText().equals(message.text());
        }
        assertTrue(found, "No matching CMR for " + message);

    }

    private Callable<Boolean> anyMtAccepted(ConcurrentLinkedQueue<TelnyxMessageService.IdMessage> sentMessages) {
        return () -> !sentMessages.isEmpty();
    }

    private Callable<Boolean> anyMtErrors(ConcurrentLinkedQueue<TelnyxMessageService.MessageErrorList> messages) {
        return () -> !messages.isEmpty();
    }

}
