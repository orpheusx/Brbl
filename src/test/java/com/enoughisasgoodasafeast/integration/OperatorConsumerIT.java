package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.operator.ProcessState;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.Message.newMO;
import static com.enoughisasgoodasafeast.RabbitQueueFunctions.*;
import static com.enoughisasgoodasafeast.RabbitQueueConsumer.RETRY_QUEUE_SUFFIX;
import static com.enoughisasgoodasafeast.RetryDelayRoutingKey.DELAY_5S;
import static com.enoughisasgoodasafeast.SharedConstants.*;
import static com.enoughisasgoodasafeast.integration.IntegrationTestFunctions.loadPropertiesWithContainerOverrides;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link OperatorConsumer#handleDelivery}.
 *
 * <p>These tests verify the RabbitMQ queueing side effects produced by each {@link ProcessState}
 * value that {@link SessionAwareMessageProcessor#process} may return, without involving real
 * database or script-processing logic. A {@link StubSessionAwareMessageProcessor} controls which
 * state is returned and whether {@code complete()} succeeds or throws.
 *
 * <p>A single {@link RabbitMQContainer} is started once for the class. The queues and exchanges
 * are created fresh for each test by constructing a new {@link RabbitQueueConsumer} in
 * {@link #setUp()}.
 */
@Testcontainers
public class OperatorConsumerIT {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorConsumerIT.class);

    /** A simple inbound MO message used across all tests. */
    private static final Message TEST_MO = newMO("15551234567", "54321", "hello");

    private static final RabbitMQContainer brokerContainer =
            new RabbitMQContainer("rabbitmq:4.3-management-alpine");

    private static Properties testProps;
    private static String primaryQueueName;
    private static String failedQueueName;
    private static String retryQueueName; // the DELAY_5S bucket

    // Per-test instances
    private StubSessionAwareMessageProcessor stub;
    private QueueProducer rcvrSurrogate;
    private QueueConsumer operatorConsumer;
    private DLQLogger failQueueLogger;
    private DLQLogger retryQueueLogger;
    /** Dedicated channel for passive queue inspection and purging. Kept open for the test lifetime. */
    private Channel inspectionChannel;

    // ---- Lifecycle -------------------------------------------------------

    @BeforeAll
    static void startBrokerForAllTests() throws IOException {
        brokerContainer.start();
        testProps = loadPropertiesWithContainerOverrides(brokerContainer, "operator_consumer_it.properties");

        primaryQueueName = testProps.getProperty(CONSUMER_QUEUE_NAME);
        failedQueueName  = failQueueForQueue(primaryQueueName);
        retryQueueName   = delayQueueForRoutingKey(primaryQueueName, DELAY_5S);

        LOG.info("Primary queue:  {}", primaryQueueName);
        LOG.info("Failed queue:   {}", failedQueueName);
        LOG.info("Retry queue:    {}", retryQueueName);
    }

    @AfterAll
    static void stopContainer() {
        brokerContainer.stop();
    }

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        stub = new StubSessionAwareMessageProcessor();

        // RabbitQueueConsumer creates the queues/exchanges and starts consuming.
        operatorConsumer = RabbitQueueConsumer.createQueueConsumer(testProps, stub);

        // Open a long-lived channel for inspection and purging.
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(testProps.getProperty(PRODUCER_QUEUE_HOST));
        factory.setPort(Integer.parseInt(testProps.getProperty(PRODUCER_QUEUE_PORT)));
        inspectionChannel = factory.newConnection().createChannel();

        // Purge all queues so stale messages from a prior test (e.g. RETRY TTL bounce-backs)
        // cannot interfere with the current test.
        inspectionChannel.queuePurge(primaryQueueName);
        inspectionChannel.queuePurge(failedQueueName);
        inspectionChannel.queuePurge(retryQueueName);

        // The rcvrSurrogate publishes MOs into the primary queue.
        rcvrSurrogate = RabbitQueueProducer.createQueueProducer(testProps);

        // Watchers for the side effect queues
        failQueueLogger  = DLQLogger.createDLQLogger(testProps);
        retryQueueLogger = DLQLogger.createDLQLogger(testProps, retryQueueName);
    }

    @AfterEach
    void tearDown() {
        // Shut down the consumer first so its RabbitMQ channel is closed cleanly before
        // anything else. A PRECONDITION_FAILED protocol error on the consumer channel
        // would otherwise leak into the next test's setUp().
        try {
            operatorConsumer.shutdown();
        } catch (Exception e) {
            LOG.warn("Exception shutting down operatorConsumer: {}", e.getMessage());
        }

        try {
            failQueueLogger.stopConsuming();
            retryQueueLogger.stopConsuming();
            rcvrSurrogate.shutdown();
        } catch (Exception e) {
            LOG.warn("Exception during DLQ/surrogate tearDown: {}", e.getMessage());
        }

        if (inspectionChannel != null && inspectionChannel.isOpen()) {
            try {
                inspectionChannel.getConnection().close();
            } catch (Exception e) {
                LOG.warn("Could not close inspection channel: {}", e.getMessage());
            }
        }
    }

    // ---- Tests -----------------------------------------------------------

    /**
     * When {@code process()} returns {@link ProcessState#OK} and {@code complete()} succeeds,
     * the MO must be acked (primary queue depth returns to 0) and nothing must appear in the
     * failed or retry queues.
     */
    @Test
    void processState_OK_messageIsAcked() throws IOException, TimeoutException {
        stub.setReturnedProcessState(ProcessState.OK);

        rcvrSurrogate.enqueue(TEST_MO);

        await().atMost(3, SECONDS).until(primaryQueueIsEmpty());

        assertEquals(0, getQueueDepth(primaryQueueName),
                "Primary queue should be empty after OK ack");
        assertTrue(failQueueLogger.getDeadMessages().isEmpty(),
                "Failed queue must be empty for ProcessState.OK");
        assertTrue(retryQueueLogger.getDeadMessages().isEmpty(),
                "Retry queue must be empty for ProcessState.OK");
        assertEquals(1, stub.getProcessCallCount(),  "process() should be called exactly once");
        assertEquals(1, stub.getCompleteCallCount(), "complete() should be called exactly once on OK");
    }

    /**
     * When {@code process()} returns {@link ProcessState#OK} but {@code complete()} throws
     * (simulating a DB commit failure), the MO must be routed to the failed queue and then
     * acked from the primary queue. Nothing should end up in the retry queue.
     */
    @Test
    void processState_OK_completeFails_messageGoesToFailedQueue() throws IOException, TimeoutException {
        stub.setReturnedProcessState(ProcessState.OK)
            .setThrowsOnComplete(true);

        rcvrSurrogate.enqueue(TEST_MO);

        await().atMost(3, SECONDS).until(anyMessagesIn(failQueueLogger));

        assertEquals(1, failQueueLogger.getDeadMessages().size(),
                "Failed queue should contain exactly 1 message");
        assertEquals(TEST_MO.text(), failQueueLogger.getDeadMessages().getFirst().text(),
                "Failed queue message text should match the original MO");
        assertEquals(0, getQueueDepth(primaryQueueName),
                "Primary queue should be empty (message was acked after routing to fail queue)");
        assertTrue(retryQueueLogger.getDeadMessages().isEmpty(),
                "Retry queue must be empty on a commit failure");
        assertEquals(1, stub.getProcessCallCount());
        assertEquals(1, stub.getCompleteCallCount(),
                "complete() must have been attempted even though it threw");

        failQueueLogger.clearDeadMessages();
    }

    /**
     * When {@code process()} returns {@link ProcessState#ERROR} the MO must be published to the
     * failed queue and acked from the primary queue. {@code complete()} must NOT be called.
     */
    @Test
    void processState_ERROR_messageGoesToFailedQueue() throws IOException, TimeoutException {
        stub.setReturnedProcessState(ProcessState.ERROR);

        rcvrSurrogate.enqueue(TEST_MO);

        await().atMost(3, SECONDS).until(anyMessagesIn(failQueueLogger));

        assertEquals(1, failQueueLogger.getDeadMessages().size(),
                "Failed queue should contain exactly 1 message on ERROR");
        assertEquals(TEST_MO.text(), failQueueLogger.getDeadMessages().getFirst().text(),
                "Failed queue message text should match the original MO");
        assertEquals(0, getQueueDepth(primaryQueueName),
                "Primary queue should be empty after ERROR routing");
        assertTrue(retryQueueLogger.getDeadMessages().isEmpty(),
                "Retry queue must be empty for ProcessState.ERROR");
        assertEquals(1, stub.getProcessCallCount());
        assertEquals(0, stub.getCompleteCallCount(),
                "complete() must NOT be called when process() returns ERROR");

        failQueueLogger.clearDeadMessages();
    }

    /**
     * When {@code process()} returns {@link ProcessState#NOOP} the MO must be silently acked
     * and nothing must appear in the failed or retry queues.
     */
    @Test
    void processState_NOOP_messageIsAcked() throws IOException, TimeoutException {
        stub.setReturnedProcessState(ProcessState.NOOP);

        rcvrSurrogate.enqueue(TEST_MO);

        await().atMost(3, SECONDS).until(primaryQueueIsEmpty());

        assertEquals(0, getQueueDepth(primaryQueueName),
                "Primary queue should be empty after NOOP ack");
        assertTrue(failQueueLogger.getDeadMessages().isEmpty(),
                "Failed queue must be empty for ProcessState.NOOP");
        assertTrue(retryQueueLogger.getDeadMessages().isEmpty(),
                "Retry queue must be empty for ProcessState.NOOP");
        assertEquals(1, stub.getProcessCallCount());
        assertEquals(0, stub.getCompleteCallCount(),
                "complete() must NOT be called for NOOP");
    }

    /**
     * When {@code process()} returns {@link ProcessState#RETRY} on the first attempt
     * ({@code x-death} count == 0), the MO must be routed to the DELAY_5S retry bucket and
     * acked from the primary queue. Nothing must appear in the failed queue.
     *
     * <p>Note: the retry queue has a TTL so the message will re-enter the primary queue after
     * 5 seconds. This test only checks the initial routing, not re-delivery.
     */
    @Test
    void processState_RETRY_messageGoesToRetryQueue() throws IOException {
        stub.setReturnedProcessState(ProcessState.RETRY);

        rcvrSurrogate.enqueue(TEST_MO);

        // The message should appear in the retry (delay-bucket) queue watcher.
        // Allow 5s: the RETRY path routes through the retry exchange before landing in the
        // delay-bucket queue, so it needs slightly more slack than the direct-publish paths.
        await().atMost(5, SECONDS).until(anyMessagesIn(retryQueueLogger));

        assertEquals(1, retryQueueLogger.getDeadMessages().size(),
                "Retry queue should contain exactly 1 message on first RETRY");
        assertEquals(TEST_MO.text(), retryQueueLogger.getDeadMessages().getFirst().text(),
                "Retry queue message text should match the original MO");
        assertEquals(0, getQueueDepth(primaryQueueName),
                "Primary queue should be empty after RETRY routing (message was acked)");
        assertTrue(failQueueLogger.getDeadMessages().isEmpty(),
                "Failed queue must be empty for ProcessState.RETRY (first attempt)");
        assertEquals(1, stub.getProcessCallCount());
        assertEquals(0, stub.getCompleteCallCount(),
                "complete() must NOT be called for RETRY");

        retryQueueLogger.clearDeadMessages();
    }

    // ---- Helpers ---------------------------------------------------------

    /** Returns the current ready-message count for the named queue using the shared inspection channel. */
    private int getQueueDepth(String queueName) throws IOException {
        AMQP.Queue.DeclareOk info = inspectionChannel.queueDeclarePassive(queueName);
        return info.getMessageCount();
    }

    private Callable<Boolean> primaryQueueIsEmpty() {
        return () -> {
            try {
                return getQueueDepth(primaryQueueName) == 0;
            } catch (Exception e) {
                return false;
            }
        };
    }

    private Callable<Boolean> anyMessagesIn(DLQLogger logger) {
        return () -> !logger.getDeadMessages().isEmpty();
    }
}
