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
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.integration.IntegrationTestFunctions.loadPropertiesWithContainerOverrides;
import static com.enoughisasgoodasafeast.sndr.sim.server.TelnyxMessageService.SIGNAL_429_TOO_MANY_RETRY_AFTER;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
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

    @Test
    void sendFailingMessageViaBroker() {
        // A message where the "to" and "from" fields are wrong.
        final var message = new Message(MessageType.MT, "+1781456F0F0", "17817209452", "_");
        final boolean enqueued = opr8rSurrogate.enqueue(message);
        assertTrue(enqueued);

        // Wait to find out if the message was sent.
        final var failedMessages = TelnyxServerMain.getTelnyxMessageService().failedMessages;
        await().atMost(3, SECONDS).until(anyMtErrors(failedMessages));

        assertEquals(1, failedMessages.size());

        final var messageErrorList = failedMessages.stream().findFirst().orElseGet(Assertions::fail);

        var failedCmr = messageErrorList.createMessageRequest();
        assertEquals(message.to(), failedCmr.getTo());
        assertEquals(message.from(), failedCmr.getFrom());
        assertEquals(message.text(), failedCmr.getText());
    }

    @Test
    void sendRetryMessageViaBroker() {
        var telnyxSignalledDelay = "=2";
        var msgRequireRetry = new Message(MessageType.MT, "+17814567890", "+17817209452",
                SIGNAL_429_TOO_MANY_RETRY_AFTER + telnyxSignalledDelay); //
        final boolean enqueued = opr8rSurrogate.enqueue(msgRequireRetry);
        assertTrue(enqueued);

        long awaitTime =  1_000 + RetryDelayRoutingKey.DELAY_5S.delayMs(); // NB: possible cause of test flakiness here...

        // Wait to find out if the message was sent.
        final var retriedMessages = TelnyxServerMain.getTelnyxMessageService().retriedMessages;
        await().atMost(awaitTime, MILLISECONDS).until(mtRetryCount(retriedMessages, 1));
        // LOG.info("Check 1");
        await().atMost(awaitTime, MILLISECONDS).until(mtRetryCount(retriedMessages, 2));
        // LOG.info("Check 2");
        await().atMost(awaitTime, MILLISECONDS).until(mtRetryCount(retriedMessages, 3));
        // LOG.info("Check 3");
        // await().atMost(awaitTime, MILLISECONDS).until(mtRetryCount(retriedMessages, 4));
        // LOG.info("Check 4");

        // The Telnyx sim server has never failed this message but the TelnyxSender has decided to stop retrying so we
        //  need look at the DLQLogger.
        await().atMost(2, SECONDS).until(anyDeadMessages(dlqLogger.getDeadMessages()));
    }

    private Callable<Boolean> anyDeadMessages(List<Message> deadMessages) {
        return () -> !deadMessages.isEmpty();
    }

    private Callable<Boolean> anyMtAccepted(ConcurrentLinkedQueue<TelnyxMessageService.IdMessage> sentMessages) {
        return () -> !sentMessages.isEmpty();
    }

    private Callable<Boolean> anyMtErrors(ConcurrentLinkedQueue<TelnyxMessageService.MessageErrorList> messages) {
        return () -> !messages.isEmpty();
    }

    private Callable<Boolean> mtRetryCount(ConcurrentLinkedQueue<TelnyxMessageService.MessageErrorList> messages, int expected) {
        return () -> messages.size() >= expected;
    }

}
