package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.datagen.KnownData;
import com.enoughisasgoodasafeast.operator.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static com.enoughisasgoodasafeast.Message.newMO;
import static com.enoughisasgoodasafeast.RabbitQueueFunctions.*;
import static com.enoughisasgoodasafeast.SharedConstants.*;
import static com.enoughisasgoodasafeast.integration.IntegrationTestFunctions.loadPropertiesWithContainerOverrides;
import static com.enoughisasgoodasafeast.operator.TestingPersistenceManager.SCRIPT_ID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Use the RabbitMQ container image to provide a message broker for message flow testing.
 * We start it once at the beginning of the class, get its running configuration, and reuse it for each test.
 * By contrast, the Operator and its components are created for each test.
 */
@Testcontainers
public class OperatorMessageFlowIT {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorMessageFlowIT.class);

    public static final String MOBILE_MX = "526641112222"; // Mexico City, MX
    public static final String SHORT_CODE = "45678";

    public static final Message keywordMO = newMO(
            MOBILE_MX, SHORT_CODE, "Color quiz"
    );
    public static final Message routableMessage = newMO(
            KnownData.knownNumbersForUsers[0], KnownData.knownRouteIdsAndChannels[2][1] /* "12124468003" */, "Color quiz"
    );

    public static final Message flortMO = newMO(
            KnownData.knownNumbersForUsers[0], KnownData.knownRouteIdsAndChannels[2][1], "flort"
    );
    public static final Message unexpectedMO = newMO(
            MOBILE_MX, SHORT_CODE, "blargh"
    );
    public static final Message changeTopicMO = newMO(
            MOBILE_MX, SHORT_CODE, "change topic"
    );
    public static final Message selectWolverinesMO = newMO(
            MOBILE_MX, SHORT_CODE, "wolverines"
    );

    private static final RabbitMQContainer brokerContainer = new RabbitMQContainer(
            "rabbitmq:4.3-management-alpine");
    private static Properties testProps;

    private QueueProducer rcvrSurrogate;
    private InMemoryQueueProducer operatorProducer;
    private TestingPersistenceManager persistenceManager;
    private Operator operator;
    private DLQLogger dlqLogger;

    @BeforeAll
    static void startBrokerForAllTests() throws IOException {
        brokerContainer.start();
        testProps = loadPropertiesWithContainerOverrides(brokerContainer, "operator_message_flow_it.properties");
    }

    @AfterAll
    static void stopContainer() {
        brokerContainer.stop();
    }

    @BeforeEach
    void setUp() throws IOException, TimeoutException, PersistenceManagerException {
        rcvrSurrogate = RabbitQueueProducer.createQueueProducer(testProps); // sends input MOs to the queue Operator consumes
        operatorProducer = new InMemoryQueueProducer(); // sink for Operator produced MTs
        persistenceManager = new TestingPersistenceManager();
        operator = new Operator(null, operatorProducer, persistenceManager);
        operator.init(testProps);

        try {
            dlqLogger = DLQLogger.createDLQLogger(testProps);
        } catch (Exception e) {
            fail("Failed to create DLQ logger:", e);
            return;
        }
    }

    @AfterEach
    void tearDown() throws IOException, TimeoutException {
        try {
            dlqLogger.stopConsuming();
            LOG.info("DLQ consumer shut down");
            rcvrSurrogate.shutdown();
        } catch (ShutdownSignalException e) {
            LOG.warn(e.getMessage());
        }
        operator.shutdown();
    }

    @Test
    public void basicConfig() throws IOException, TimeoutException {
        var expectedQueueName = testProps.getProperty(CONSUMER_QUEUE_NAME);
        var expectedFailQueue = failQueueForQueue(expectedQueueName);
        var expectedExchangeName = exchangeForQueueName(expectedQueueName);
        var expectedRetryExchangeName = retryForExchangeName(expectedExchangeName);

        var channel = getChannel();

        try {
            final var queueDeclareOk = channel.queueDeclarePassive(expectedQueueName);
            int consumerCount = queueDeclareOk.getConsumerCount();
            assertTrue(1 <= consumerCount); // useful for debugging tests?
        } catch (IOException e) {
            fail("Expected queue not found: " + expectedQueueName);
        }

        try {
            // If the method doesn't throw then the queue exists.
            final AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(expectedFailQueue);
        } catch (IOException e) {
            fail("Expected fail queue not found: " + expectedFailQueue);
        }

        try {
            // If the method doesn't throw then the exchange exists.
            final var exchangeDeclareOk = channel.exchangeDeclarePassive(expectedExchangeName);
        } catch (IOException e) {
            fail("Expected exchange not found: " + expectedExchangeName);
        }

        try {
            // If the method doesn't throw then the exchange exists.
            final var retryExchangeDeclareOk = channel.exchangeDeclarePassive(expectedRetryExchangeName);
        } catch (IOException e) {
            fail("Expected retry exchange not found: " + expectedRetryExchangeName);
        }
    }

    @Test
    public void failedMessageNoRoute() {
        try {
            // A message specifying a keyword/route that doesn't exist will trigger an error, causing it to be sent to dead-letter-queue.
            // We're using this case just to test queue handling. If/when we find another case where messages are failed w/out first retrying
            // we can test them here.
            // Note: In practice, the Rcvr should guard against this situation by rejecting unknown routes. This is TBD.
            rcvrSurrogate.enqueue(unexpectedMO);
            // Wait until we see a message show up in the failed (aka dead-letter) queue
            await().atMost(3, SECONDS).until(anyDeadLettersEnqueued(dlqLogger));
            assertFalse(dlqLogger.getDeadMessages().isEmpty(), "No messages in failed queue."); // redundant check
            // Make sure it's the one we just sent.
            assertEquals(unexpectedMO.text(), dlqLogger.getDeadMessages().getFirst().text(), "Wrong message in failed queue.");
        } finally {
            dlqLogger.clearDeadMessages();
        }
    }

    @Test
    public void failedMessageInsertNewUser() {
        // Configure the system such that it should be able to process the incoming message
        //  but set the persistence manager to fail when writing the new user to the database.
        //  This should cause the return state to be FAIL.
        try {
            LOG.info("Starting second fail check");
            var startNode = buildNodeGraph();
            var startNodeId = startNode.id();
            persistenceManager.addNodeGraph(startNodeId, startNode);

            var optInNode = buildOptInNode();
            var optInNodeId = optInNode.id();

            // for now, use same Node referenced by keyword
            var defaultNodeId = startNodeId; // TODO make separate to exercise keyword vs route default selection.

            persistenceManager.addNodeGraph(optInNode.id(), optInNode);
            final Route[] routes = registerCompatibleRoute(routableMessage, defaultNodeId, optInNodeId);
            assertEquals(routes.length, persistenceManager.getActiveRoutes().length); // dupe
            assertSame(routes[0], persistenceManager.getActiveRoutes()[0]); // dupe

            registerMatchingKeyword(Pattern.compile("(color|colour|colr).*(quiz|q|kwiz)"), startNode, routableMessage.to());

            persistenceManager.failInsertNewUser(true);

            rcvrSurrogate.enqueue(routableMessage); // Ok, start the conversation.
            await().atMost(5, SECONDS).until(anyDeadLettersEnqueued(dlqLogger));
            assertFalse(dlqLogger.getDeadMessages().isEmpty(), "No messages in failed queue."); // redundant check
            // Make sure it's the one we just sent.
            assertEquals(routableMessage.text(), dlqLogger.getDeadMessages().getFirst().text(), "Wrong message in failed queue.");

        }  finally {
            dlqLogger.clearDeadMessages();
        }
    }

    @Test
    public void processMessageBypassingQueue() {
        var startNode = buildNodeGraph();
        var startNodeId = startNode.id();
        persistenceManager.addNodeGraph(startNodeId, startNode);

        var optInNode = buildOptInNode();
        var optInNodeId = optInNode.id();

        // for now, use same Node referenced by keyword
        var defaultNodeId = startNodeId; // TODO make separate to exercise keyword vs route default selection.

        persistenceManager.addNodeGraph(optInNode.id(), optInNode);
        final Route[] routes = registerCompatibleRoute(routableMessage, defaultNodeId, optInNodeId);
        assertEquals(routes.length, persistenceManager.getActiveRoutes().length);
        assertSame(routes[0], persistenceManager.getActiveRoutes()[0]);

        var pattern = Pattern.compile("(color|colour|colr).*(quiz|q|kwiz)");
        var keyword = registerMatchingKeyword(pattern, startNode, routableMessage.to());

        var fetchedNode = persistenceManager.getNodeGraph(startNodeId);
        assertSame(startNode, fetchedNode);
        var fetchedKeyword = persistenceManager.getKeywords().get(pattern);
        assertSame(keyword, fetchedKeyword);

        var pss = operator.process(routableMessage);
        LOG.info(pss.toString());

    }

    @Test
    public void messageFlow() {
        var startNode = buildNodeGraph();
        var startNodeId = startNode.id();
        persistenceManager.addNodeGraph(startNode.id(), startNode);

        var optInNode = buildOptInNode();
        var optInNodeId = optInNode.id();
        persistenceManager.addNodeGraph(optInNodeId, optInNode);

        // for now, use same Node referenced by keyword
        var defaultNodeId = startNodeId; // TODO make a separate one to distinguish processing behavior.

        final Route[] routes = registerCompatibleRoute(routableMessage, defaultNodeId, optInNodeId);

        assertEquals(routes.length, persistenceManager.getActiveRoutes().length);
        var activeRoute = persistenceManager.getActiveRoutes()[0];
        assertSame(routes[0], activeRoute);
        assertEquals(optInNodeId, activeRoute.optInNodeId());

        var pattern = Pattern.compile("(color|colour|colr).*(quiz|q|kwiz)");
        var keyword = registerMatchingKeyword(pattern, startNode, routableMessage.to());

        assertSame(startNode, persistenceManager.getNodeGraph(startNodeId));
        assertSame(optInNode, persistenceManager.getNodeGraph(optInNodeId));
        assertSame(startNode, persistenceManager.getNodeGraph(defaultNodeId)); // keyword and default are the same

        var fetchedKeyword = persistenceManager.getKeywords().get(pattern);
        assertSame(keyword, fetchedKeyword);

        rcvrSurrogate.enqueue(routableMessage); // Ok, start the conversation.
        await().atMost(2, SECONDS).until(anyMTResponsesEnqueued(operatorProducer));

        List<Message> queuedMessages = operatorProducer.enqueued();
        assertEquals(2, queuedMessages.size());

        var optInMT = queuedMessages.get(0); // new user expects opt-in first...
        assertNotNull(optInMT);
        assertEquals(routableMessage.from(), optInMT.to());
        assertEquals(routableMessage.to(), optInMT.from());
        assertTrue(optInMT.text().contains("opted in"));
        //LOG.info("New session opt-in: {}", optInMT.text());

        var responseMT = queuedMessages.get(1); // ...then the actual scripted response
        assertNotNull(responseMT);
        assertEquals(routableMessage.from(), responseMT.to());
        assertEquals(routableMessage.to(), responseMT.from());
        assertTrue(responseMT.text().contains("favorite color"));
        //LOG.info("Second message: {}", responseMT.text());

        queuedMessages.clear();

        rcvrSurrogate.enqueue(flortMO); // user answers the question
        await().atMost(2, SECONDS).until(anyMTResponsesEnqueued(operatorProducer));
        Message flortMT = operatorProducer.enqueued().getFirst();
        assertNotNull(flortMT);
        //LOG.info("flortMT: {}", flortMT);
        assertEquals(flortMO.from(), flortMT.to());
        assertEquals(flortMO.to(), flortMT.from());
        assertTrue(flortMT.text().contains("for the cool kids"));

        queuedMessages.clear();

        // Add the cases from messageFlowWithUnexpectedInputAndChangeTopicRequested
        //  and messageFlowWithUnexpectedInputAndChangeTopicRequested
        // ...
    }

    /**
     * Currently there are places in the Operator processing that return ProcessState.RETRY.
     * This is left as a placeholder in case we
     */
    // @Test
    public void retriableMessage() {
        var startNode = buildNodeGraph();
        persistenceManager.addNodeGraph(startNode.id(), startNode);

        var optInNode = buildOptInNode();
        persistenceManager.addNodeGraph(optInNode.id(), optInNode);

        final var routes = registerCompatibleRoute(routableMessage, startNode.id(), optInNode.id());
        final var keyword = registerMatchingKeyword(Pattern.compile("color"), startNode, routableMessage.to());

        assertEquals(routes.length, persistenceManager.getActiveRoutes().length);
        var activeRoute = persistenceManager.getActiveRoutes()[0];
        assertSame(routes[0], activeRoute);
        assertEquals(optInNode.id(), activeRoute.optInNodeId());

        rcvrSurrogate.enqueue(routableMessage);
    }

    //    @Test
    public void messageFlowWithUnexpectedInput() {
        assertDoesNotThrow(() -> {
            rcvrSurrogate.enqueue(keywordMO);
            await().atMost(5, SECONDS).until(anyMTResponsesEnqueued(operatorProducer));

            List<Message> queuedMessages = operatorProducer.enqueued();

            Message colorQuizMT = operatorProducer.enqueued().getFirst();
            assertNotNull(colorQuizMT);
            assertEquals(keywordMO.from(), colorQuizMT.to());
            assertEquals(keywordMO.to(), colorQuizMT.from());
            assertTrue(colorQuizMT.text().contains("What's you favorite color?"));

            queuedMessages.clear();

            rcvrSurrogate.enqueue(unexpectedMO);
            await().atMost(5, SECONDS).until(anyMTResponsesEnqueued(operatorProducer));

            Message errorMessage = operatorProducer.enqueued().getFirst();
            assertNotNull(errorMessage);
            assertEquals(keywordMO.from(), errorMessage.to());
            assertEquals(keywordMO.to(), errorMessage.from());
            assertTrue(errorMessage.text().contains("Try again"));

            queuedMessages.clear();

        });
    }

    //    @Test
    public void messageFlowWithUnexpectedInputAndChangeTopicRequested() {
        assertDoesNotThrow(() -> {
            rcvrSurrogate.enqueue(keywordMO);
            await().atMost(5, SECONDS).until(anyMTResponsesEnqueued(operatorProducer));

            List<Message> queuedMessages = operatorProducer.enqueued();

            Message colorQuizMT = operatorProducer.enqueued().getFirst();
            assertNotNull(colorQuizMT);
            assertEquals(keywordMO.from(), colorQuizMT.to());
            assertEquals(keywordMO.to(), colorQuizMT.from());
            assertTrue(colorQuizMT.text().contains("What's you favorite color?"));

            queuedMessages.clear();

            rcvrSurrogate.enqueue(unexpectedMO);
            await().atMost(5, SECONDS).until(anyMTResponsesEnqueued(operatorProducer));

            Message errorMessage = operatorProducer.enqueued().getFirst();
            assertNotNull(errorMessage);
            assertEquals(keywordMO.from(), errorMessage.to());
            assertEquals(keywordMO.to(), errorMessage.from());
            assertTrue(errorMessage.text().contains("Try again"));

            queuedMessages.clear();

            rcvrSurrogate.enqueue(changeTopicMO);
            await().atMost(5, SECONDS).until(anyMTResponsesEnqueued(operatorProducer));

            Message acknowledgeTopicChange = queuedMessages.getFirst();
            assertNotNull(acknowledgeTopicChange);
            assertEquals(keywordMO.from(), acknowledgeTopicChange.to());
            assertEquals(keywordMO.to(), acknowledgeTopicChange.from());
            assertTrue(acknowledgeTopicChange.text().contains("You want to talk about something else? OK"));

            Message availableTopicMessage = queuedMessages.get(1);
            assertNotNull(availableTopicMessage);
            assertEquals(keywordMO.from(), availableTopicMessage.to());
            assertEquals(keywordMO.to(), availableTopicMessage.from());

            queuedMessages.clear();

            String topicText = availableTopicMessage.text();
            assertTrue(topicText.contains("topics I can talk about"));
            assertTrue(topicText.contains("wolverines"));
            assertTrue(topicText.contains("international monetary policy"));

            rcvrSurrogate.enqueue(selectWolverinesMO);
            await().atMost(5, SECONDS).until(anyMTResponsesEnqueued(operatorProducer));

            Message confirmWolverineMO = queuedMessages.getFirst();
            assertNotNull(confirmWolverineMO);
            assertEquals(keywordMO.from(), confirmWolverineMO.to());
            assertEquals(keywordMO.to(), confirmWolverineMO.from());
            assertTrue(confirmWolverineMO.text().contains("pointy teeth"));

        });
    }

    private Callable<Boolean> anyMTResponsesEnqueued(InMemoryQueueProducer operatorProducer) {
        return () -> !operatorProducer.enqueued().isEmpty();
    }

    private Callable<Boolean> anyDeadLettersEnqueued(DLQLogger dlqLogger) {
        return () -> !dlqLogger.getDeadMessages().isEmpty();
    }

    private Callable<Boolean> anyMessagesInRetryQueue(AMQP.Queue.DeclareOk declareOk) {
        return () -> !(declareOk.getMessageCount() == 0);
    }

    private AMQP.Queue.DeclareOk getRetryQueueInfo() throws IOException, TimeoutException {
        var expectedRetryExchangeName =
                retryForExchangeName(
                        exchangeForQueueName(
                                testProps.getProperty(CONSUMER_QUEUE_NAME)
                        )
                );

        return getChannel().queueDeclarePassive(expectedRetryExchangeName);
    }

    private Channel getChannel() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(testProps.getProperty(SharedConstants.PRODUCER_QUEUE_HOST));
        factory.setPort(Integer.parseInt(testProps.getProperty(SharedConstants.PRODUCER_QUEUE_PORT)));
        return factory.newConnection().createChannel();
    }


    /**
     * Construct a single message, opt-in graph. Operator assumes (fairly) that every Route will define an opt-in graph.
     *
     * @return the head of the assembled Node graph.
     */
    private Node buildOptInNode() {
        var optInNow = new Node("Welcome! You are now opted in.", NodeType.SEND_MESSAGE, "AnOptInMessage");
        optInNow.edges().add(new Edge());
        return optInNow;
    }

    /**
     * Construct a short graph of nodes for testing purposes
     * Following the principles of Don't Repeat Yourself, we should merge this with the very similar method that exists in OperatorTest.
     *
     * @return the head of the assembled Node graph.
     */
    private Node buildNodeGraph() {

        Node presentQuestion = new Node(SCRIPT_ID, OperatorTest.COLOR_QUIZ_START_TEXT, NodeType.PRESENT_MULTI); // label: "ColorQuizStart"
        Node processAnswer = new Node(OperatorTest.COLOR_QUIZ_UNEXPECTED_INPUT, NodeType.PROCESS_MULTI, "ColorQuizProcessResponse");
        presentQuestion.edges().add(
                new Edge(List.of("n/a"), "n/a", processAnswer)
        );

        Node endConversation = new Node(OperatorTest.COLOR_QUIZ_END_CONVERSATION, NodeType.END_OF_CHAT, "ColorQuizEnd");

        Edge answerRed = new Edge(List.of("red"), "Red is the color of life.", endConversation);
        Edge answerBlue = new Edge(List.of("blue"), "Blue is my fave, as well.", endConversation);
        Edge answerFlort = new Edge(List.of("flort"), "Flort is for the cool kids.", endConversation);

        processAnswer.edges().addAll(List.of(answerRed, answerBlue, answerFlort));
        return presentQuestion;
    }

    // Consider moving this into the TestingPersistenceManager itself.
    private @NonNull Keyword registerMatchingKeyword(Pattern pattern, Node nodeGraph, String channel) {
        var keyword = new Keyword(UUID.randomUUID(), "", routableMessage.platform(), nodeGraph.id(), channel);
        persistenceManager.addKeyword(pattern, keyword);
        return keyword;
    }

    // Consider moving this into the TestingPersistenceManager itself.
    private @NonNull Route[] registerCompatibleRoute(Message message, UUID defaultNodeId, UUID optInNodeId) {
        Route[] routes = {
                new Route(
                        message.platform(),
                        message.to(),
                        defaultNodeId,
                        UUID.fromString(KnownData.knownCompanyId),
                        /*interruptNodeId*/null, // should never be null in practice
                        optInNodeId,
                        /*optOutNodeId*/null)    // should never be null in practice
        };
        persistenceManager.setActiveRoutes(routes);
        return routes;
    }

}
