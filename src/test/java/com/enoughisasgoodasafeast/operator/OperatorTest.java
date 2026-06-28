package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static com.enoughisasgoodasafeast.Message.newMO;
import static com.enoughisasgoodasafeast.Message.newMT;
import static java.io.IO.println;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

public class OperatorTest {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorTest.class);

    public static final String MOBILE_CA = "14385551234";  // Quebec, CA
    public static final String MOBILE_MX = "522005551234"; // Mexico City, MX
    public static final String MOBILE_US = "7815551234";   // Fake US number.
    public static final String SHORT_CODE_1 = "1234";
    public static final String SHORT_CODE_2 = "2345";
    public static final String SHORT_CODE_3 = "3456";
    public static final String SHORT_CODE_4 = "12124468003";
    public static final String MO_TEXT_1 = "Hello Brbl";
    public static final String MO_TEXT_2 = "Howdy Brbl";
    public static final String INITIAL_SCRIPT_RESPONSE = "The initial response to the user MO";

    public static final String COLOR_QUIZ_KEYWORD = "Color quiz";
    public static final String COLOR_QUIZ_START_TEXT = "What is your favorite color? 1) red 2) blue 3) flort";
    public static final String COLOR_QUIZ_UNEXPECTED_INPUT = "Sorry, please pick one of the choices by name or number.";
    public static final String COLOR_QUIZ_END_CONVERSATION = "Talk to you later.";

    public static final Message mo1 = newMO(
            MOBILE_US, SHORT_CODE_1, MO_TEXT_1
    );
    public static final Message mo2 = newMO(
            MOBILE_MX, SHORT_CODE_1, MO_TEXT_1
    );
    public static final Message mo3 = newMO(
            MOBILE_MX, SHORT_CODE_1, "Adiós Brbl"
    );
    public static final Message mo4 = newMO(
            MOBILE_MX, SHORT_CODE_4, COLOR_QUIZ_KEYWORD
    );
    public static final Message mo5 = newMO(
            MOBILE_MX, SHORT_CODE_4, "flort"
    );
    public static final Message unexpected = newMO(
            MOBILE_MX, SHORT_CODE_4, "blargh"
    );
    public static final Message changeTopic = newMO(
            MOBILE_MX, SHORT_CODE_4, "change topic"
    );
    public static final Message wolverine = newMO(
            MOBILE_MX, SHORT_CODE_4, "wolverine"
    );

    private static final SessionKey SESSION_KEY_US_SHORT_CODE_1 = SessionKey.newSessionKey(mo1);
    private static final String WELCOME_OPT_IN_MESSAGE = "Welcome. You can opt out at any time by sending 'STOP'";
    private static final String CHINESE_OPT_IN_MESSAGE = "欢迎。您可以随时发送“STOP”以退订。";

    private InMemoryQueueProducer queueProducer;
    private Operator operator = null;

    private Edge answerFlort = null;
    private Node endConversation;
    private Node processAnswer;

    @BeforeEach
    void setup() {
        queueProducer = new InMemoryQueueProducer();
        QueueConsumer fakeQueueConsumer = new FakeQueueConsumer();
        PersistenceManager persistenceManager = new TestingPersistenceManager();
        operator = new Operator(fakeQueueConsumer, queueProducer, persistenceManager);


        // Construct a script for testing purposes
        Node presentQuestion = new Node(COLOR_QUIZ_START_TEXT, NodeType.PRESENT_MULTI, "ColorQuizStart");
        processAnswer = new Node(COLOR_QUIZ_UNEXPECTED_INPUT, NodeType.PROCESS_MULTI, "ColorQuizProcessResponse");
        presentQuestion.edges().add(
                new Edge(List.of("n/a"), "n/a", processAnswer)
        );

        endConversation = new Node(COLOR_QUIZ_END_CONVERSATION, NodeType.END_OF_CHAT, "ColorQuizEnd");

        Edge answerRed = new Edge(List.of("red"), "Red is the color of life.", endConversation);
        Edge answerBlue = new Edge(List.of("blue"), "Blue is my fave, as well.", endConversation);
        answerFlort = new Edge(List.of("flort"), "Flort is for the cool kids.", endConversation);

        processAnswer.edges().addAll(List.of(answerRed, answerBlue, answerFlort));

        // Add the test script to the fixture's script "cache."
        ((TestingPersistenceManager) persistenceManager).addScript(
                TestingPersistenceManager.SCRIPT_ID, presentQuestion);

        Node confirmChangeTopic = new Node(
                "Oh, you want to talk about something else? 1) yes 2) no, let's continue with the current conversation.",
                NodeType.PRESENT_MULTI,
                "ConfirmChangeTopic");
        Node processChangeResponse = new Node("yes or no, please", NodeType.PROCESS_MULTI, "ProcessChangeResponse");
        Edge linkPresentToProcess = new Edge(processChangeResponse);
        confirmChangeTopic.edges().add(linkPresentToProcess);

        Node presentTopics = new Node(
                "Here are the list of topics: 1,2,3...", NodeType.PRESENT_MULTI, "PresentTopics");
        Edge edgeToNowhere = new Edge(null);
        presentTopics.edges().add(edgeToNowhere);

        Edge confirmChange = new Edge(List.of("yes"), "Ok, cool.", presentTopics);

        // Include a placeholder that the Operator will replace when setting up the 'change topic' conversation.
        Node continueCurrentPlaceholder = new Node("N/A",	NodeType.END_OF_CHAT, "ContinueCurrentPlaceholder");
        Edge stayOnCurrent = new Edge(List.of("no"), "Ok, I'll repeat the last question.", continueCurrentPlaceholder);

        processChangeResponse.edges().addAll(List.of(confirmChange, stayOnCurrent));

        LOG.info("Setup: Interrupt node: {}", confirmChangeTopic);
        LOG.info("Setup: Interrupt node target: {}", confirmChangeTopic.edges().getFirst().targetNode());
        confirmChangeTopic.edges().getFirst().targetNode().edges().forEach(edge -> {
            LOG.info("edge: {}:{} -> node label:{} type:{}",
                    edge.matchText(), edge.responseText(), edge.targetNode().label(), edge.targetNode().type());
        });

        Node optInNode = new Node(WELCOME_OPT_IN_MESSAGE, NodeType.SEND_MESSAGE, "OptIn");
        optInNode.edges().add(new Edge(null));

        Node altOptInNode = new Node(CHINESE_OPT_IN_MESSAGE, NodeType.SEND_MESSAGE, "ChineseOptIn");
        altOptInNode.edges().add(new Edge(null));

        Node optOutNode = new Node("You have been opted out. Thanks for all the fish", NodeType.SEND_MESSAGE, "OptOut");
        optOutNode.edges().add(new Edge(null));

        // Define a default conversation graph for the platform-channel, adding it with the default route.
        Node defaultNode = new Node("Welcome! You can talk to us about the following topics...", NodeType.END_OF_CHAT, "CustomerTopicStarter");
        Route route1 = new Route(Platform.SMS, mo1.to(), defaultNode.id(), randomUUID(), confirmChangeTopic.id(), optInNode.id(), optOutNode.id());
        Route route2 = new Route(Platform.SMS, SHORT_CODE_2, defaultNode.id(), randomUUID(), confirmChangeTopic.id(), optInNode.id(), optOutNode.id());
        Route route3 = new Route(Platform.SMS, SHORT_CODE_3, defaultNode.id(), randomUUID(), confirmChangeTopic.id(), altOptInNode.id(), optOutNode.id());


        // Remember the default routes only holds the script id. We still need to add the script itself to the main cache.
        ((TestingPersistenceManager) persistenceManager).addScript(defaultNode.id(), defaultNode);

        ((TestingPersistenceManager) persistenceManager).addScript(
                confirmChangeTopic.id(), confirmChangeTopic);
        ((TestingPersistenceManager) persistenceManager).addScript(optInNode.id(), optInNode);
        ((TestingPersistenceManager) persistenceManager).addScript(altOptInNode.id(), altOptInNode);
        ((TestingPersistenceManager) persistenceManager).addScript(optInNode.id(), optInNode);
        ((TestingPersistenceManager) persistenceManager).addScript(optOutNode.id(), optOutNode);

        var onlyCompanyId = UUID.fromString("019d2055-922c-75f7-a80e-091f01382fa3");
        var allRoutes = new Route[]{
                route1,
                route2,
                route3,
                new Route(
                        randomUUID(),
                        Platform.SMS,
                        SHORT_CODE_4,
                        presentQuestion.id(), // defaultNodeId
                        onlyCompanyId,
                        RouteStatus.ACTIVE,
                        confirmChangeTopic.id(), // interruptNodeId
                        optInNode.id(), // placeholder with random id
                        optOutNode.id(), // placeholder with random id
                        Instant.now(),
                        Instant.now()
                )
        };

        ((TestingPersistenceManager) persistenceManager).setActiveRoutes(allRoutes);
    }


    @Test
    void processAndCheckResponse() {

        Node node1 = new Node(INITIAL_SCRIPT_RESPONSE, NodeType.SEND_MESSAGE, "labelForProcessAndCheckResponse");
        Edge edge1 = new Edge(List.of("1", "2", "3"), null);
        node1.edges().add(edge1);

        Message mo1 = newMO(MOBILE_CA, SHORT_CODE_1, MO_TEXT_1);
        Message mo2 = newMO(MOBILE_MX, SHORT_CODE_2, MO_TEXT_1);
        Message mo3 = newMO(MOBILE_US, SHORT_CODE_3, MO_TEXT_2);

        operator.scriptByKeywordCache.put(KeywordCacheKey.newKey(SessionKey.newSessionKey(mo1)), node1);
        operator.scriptByKeywordCache.put(KeywordCacheKey.newKey(SessionKey.newSessionKey(mo2)), node1);
        operator.scriptByKeywordCache.put(KeywordCacheKey.newKey(SessionKey.newSessionKey(mo3)), node1);

        Message mt1 = newMT(SHORT_CODE_1, MOBILE_CA, INITIAL_SCRIPT_RESPONSE);
        Message mt2 = newMT(SHORT_CODE_2, MOBILE_MX, INITIAL_SCRIPT_RESPONSE);
        Message mt3 = newMT(SHORT_CODE_3, MOBILE_US, INITIAL_SCRIPT_RESPONSE);

        assertDoesNotThrow(() -> {
            operator.process(mo1);
        });

        //IO.println("------ Messages queued for mo1 ------");
        //queueProducer.enqueued().forEach(System.out::println);
        assertEquals(2, queueProducer.enqueuedCount()); // Expect the INITIAL_SCRIPT_RESPONSE MT preceded by the OPT_IN notification.

        assertEquals(mt1.to(), queueProducer.enqueued().getFirst().to());
        assertEquals(mt1.from(), queueProducer.enqueued().getFirst().from());
        assertEquals(WELCOME_OPT_IN_MESSAGE, queueProducer.enqueued().getFirst().text());
        assertEquals(INITIAL_SCRIPT_RESPONSE, queueProducer.enqueued().get(1).text());
        assertEquals(mt1.type(), queueProducer.enqueued().getFirst().type());
        queueProducer.enqueued().clear(); // Need to manually clear the enqueued message list between process() calls.

        assertDoesNotThrow(() -> { operator.process(mo2); });
        println("------ Messages queued for mo2 ------");
        queueProducer.enqueued().forEach(System.out::println);

        assertEquals(2, queueProducer.enqueuedCount());
        assertEquals(mt2.to(), queueProducer.enqueued().get(1).to());
        assertEquals(mt2.from(), queueProducer.enqueued().get(1).from());
        assertEquals(mt2.text(), queueProducer.enqueued().get(1).text());
        assertEquals(mt2.type(), queueProducer.enqueued().get(1).type());
        queueProducer.enqueued().clear();

        // The opt-in message for the route specified by mo3 is different.
        assertDoesNotThrow(() -> { operator.process(mo3); });
        //IO.println("------ Messages queued for mo3 ------");
        //queueProducer.enqueued().forEach(System.out::println);

        assertEquals(2, queueProducer.enqueuedCount());

        assertEquals(mt3.to(), queueProducer.enqueued().getFirst().to());
        assertEquals(mt3.from(), queueProducer.enqueued().getFirst().from());
        assertEquals(CHINESE_OPT_IN_MESSAGE, queueProducer.enqueued().getFirst().text());
        assertEquals(mt3.type(), queueProducer.enqueued().getFirst().type());

        assertEquals(mt3.to(), queueProducer.enqueued().get(1).to());
        assertEquals(mt3.from(), queueProducer.enqueued().get(1).from());
        assertEquals(mt3.text(), queueProducer.enqueued().get(1).text());
        assertEquals(mt3.type(), queueProducer.enqueued().get(1).type());
        queueProducer.enqueued().clear();
    }


    @Test
    void getUserSessionUncachedCached() {
        Node node1 = new Node(INITIAL_SCRIPT_RESPONSE, NodeType.SEND_MESSAGE);
        Edge edge1 = new Edge(List.of("1", "2", "3"), null);
        node1.edges().add(edge1);

        operator.scriptByKeywordCache.put(new KeywordCacheKey(SHORT_CODE_1, Platform.SMS, MO_TEXT_1), node1);

        //MOBILE_US, SHORT_CODE_1, MO_TEXT_1
        Session s1 = operator.sessionCache.get(SessionKey.newSessionKey(mo1)); // from a US number
        Session s2 = operator.sessionCache.get(SessionKey.newSessionKey(mo2)); // from a MX number
        Session s3 = operator.sessionCache.get(SessionKey.newSessionKey(mo3)); // from same MX number

        assertNotNull(s1);
        assertNotNull(s2);
        assertNotNull(s3);

        // Sessions for two different Users are separate
        assertNotEquals(s1.getId(), s2.getId());

        // A Session, once cached, is returned for subsequent messages from the same User
        assertEquals(s2.getId(), s3.getId());
        // and has the same values (records guarantee this but...)
        assertEquals(s2.getStartTimeMicros(), s3.getStartTimeMicros());
        assertEquals(s2.getUser(), s3.getUser());

        // And are, in fact, the same instance
        assertEquals(s2, s3);

    }


    @Test
    void findMatchForKeyword() {
        final Map<Pattern, Keyword> all = operator.allKeywordsByPatternCache.get(Operator.ALL);
        assertNotNull(all);
        assertFalse(all.isEmpty());

        // NB: Regular MTs don't make sense in this situation. Only MOs and, possibly, push messages.
        assertNotSame(MessageType.MT, mo4.type());

        // The platform, channel and patter will match this message.
        var matching = KeywordCacheKey.newKey(SessionKey.newSessionKey(mo4));
        assertNotNull(operator.findMatch(all, matching), "Expected a match for " + mo4);

        // Neither the channel nor the keyword will match for this.
        var keyNotMatchingChannelOrKeyword = KeywordCacheKey.newKey(SessionKey.newSessionKey(mo3));
        assertNull(operator.findMatch(all, keyNotMatchingChannelOrKeyword));

        var keyNotMatchingKeyword = KeywordCacheKey.newKey(SessionKey.newSessionKey(mo5));
        assertNull(operator.findMatch(all, keyNotMatchingKeyword));
    }


    @Test
    void stepThroughPresentProcessMulti() {
        // Preflight check: cache is empty.
        assertEquals(0, operator.scriptByKeywordCache.estimatedSize());

        // Initiate the conversation
        assertDoesNotThrow(() -> {
            operator.process(mo4);
        });

        // The lookup from scriptCache will have the effect of populating scriptByKeywordCache
        assertEquals(1, operator.scriptByKeywordCache.estimatedSize());

        var session = operator.sessionCache.get(SessionKey.newSessionKey(mo4));
        assertNotNull(session);
        LOG.info("Session Cache size = {}", operator.sessionCache.estimatedSize());

        final String userPhoneNumber = session.getUser().platformNumbers().get(Platform.SMS);
        LOG.info("Session User platform ID = {}", userPhoneNumber);
        assertEquals(MOBILE_MX, userPhoneNumber);

        assertNotNull(session.getCurrentNode());
        Node.printGraph(session.getCurrentNode(), session.getCurrentNode(), 2);

        final List<Message> queuedMessages = queueProducer.enqueued();

        assertEquals(2, queuedMessages.size(), "Unexpected number of messages queued.");
        assertTrue(requireNonNull(queuedMessages.get(0)).text().startsWith(WELCOME_OPT_IN_MESSAGE));
        assertTrue(requireNonNull(queuedMessages.get(1)).text().contains("favorite color"), "Expected text not found in first queued message.");

        // The session's currentNode should be awaiting a response.
        // Arguably this is a bad test since it makes assumption about the internal state of the Operator/Session.
        assertEquals(NodeType.PROCESS_MULTI, session.getCurrentNode().type(), "Session node state has unexpected type.");

        // Send a valid response
        assertDoesNotThrow(() -> {
            operator.process(mo5); // answer given should select "flort"
        });

        queuedMessages.forEach(message -> {
            println("queued message: " + message.text());
        });

        // Check the rest of the MT responses.
        assertEquals(4, queuedMessages.size(), "Unexpected number of messages queued.");
        assertEquals(answerFlort.responseText(), queuedMessages.get(2).text(), "Expected text not found in 2nd queued message.");
        assertEquals(endConversation.text(), queuedMessages.get(3).text());

        // Check the evaluatedNodes.
        final List<Node> evaluatedNodes = session.getEvaluatedNodes();
        assertEquals(COLOR_QUIZ_UNEXPECTED_INPUT, evaluatedNodes.get(2).text());
        assertEquals(COLOR_QUIZ_END_CONVERSATION, evaluatedNodes.get(3).text());
        assertEquals(COLOR_QUIZ_START_TEXT, evaluatedNodes.get(1).text());

        // Check that the Session's currentNode is now the last node in the conversation
        assertNull(session.getCurrentNode(), "Session's currentNode is unexpectedly not null.");

        assertEquals(0, operator.sessionCache.estimatedSize(), "Session wasn't cleared at end of conversation.");
    }


    @Test
    void stepThroughPresentProcessMultiWithBadInput() {

        // Preflight check: cache is empty.
        assertEquals(0, operator.scriptByKeywordCache.estimatedSize());

        // Initiate the conversation
        assertDoesNotThrow(() -> {
            operator.process(mo4);
        });

        // The lookup from scriptCache will have the effect of populating scriptByKeywordCache
        assertEquals(1, operator.scriptByKeywordCache.estimatedSize());

        var session = operator.sessionCache.get(SessionKey.newSessionKey(mo4));
        assertNotNull(session);

        // Make sure we get the expected initial response.
        final List<Message> queuedMessages = queueProducer.enqueued();
        queuedMessages.forEach((message -> {println("stepThroughPresentProcessMultiWithBadInput message:" + message.text());}));
        assertEquals(2, queuedMessages.size(), "Unexpected number of messages queued.");
        // Expect the text from an OPT
        assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("STOP"), "Expected text not found in first queued message.");
        assertTrue(requireNonNull(queuedMessages.get(1)).text().contains(COLOR_QUIZ_START_TEXT),
                "Expected text not found in second queued message.");
        // The current node should now be awaiting a response
        assertEquals(NodeType.PROCESS_MULTI, session.getCurrentNode().type(), "Session node state has unexpected type.");

        queueProducer.enqueued().clear();

        // Now provide bad input to the question posed
        for (int i = 0; i < 3; i++) { // TODO At some point we should add handling for repeated failures. Until then we continue to handle bad input the same way.
            assertDoesNotThrow(() -> { operator.process(unexpected); });
            final Message errorMessage = queuedMessages.get(i);
            LOG.info("Bad input response: {}", errorMessage.text());
            assertTrue(errorMessage.text().contains(processAnswer.text()), "Expected error response not found.");
            // Since we don't advance when there's an error we should still be on the ProcessMulti
            assertEquals(NodeType.PROCESS_MULTI, session.getCurrentNode().type(), "Session node state has unexpected type.");
        }

        // Now provide good input to the question posed and check that we advance to the end of the conversation.
        assertDoesNotThrow(() -> { operator.process(mo5); });
        //producer.enqueued().forEach(message -> LOG.info(message.text()));
        assertEquals(5, queuedMessages.size(), "Unexpected number of messages queued.");
        assertEquals(processAnswer.text(), queuedMessages.get(1).text(), "Expected text not found in 1st queued message.");
        assertEquals(processAnswer.text(), queuedMessages.get(2).text(), "Expected text not found in 2nd queued message.");
        assertEquals(answerFlort.responseText(), queuedMessages.get(3).text(), "Expected text not found in 3rd queued message.");
        assertEquals(endConversation.text(), queuedMessages.get(4).text());

        // The conversation is complete.

        // Check the evaluatedNodes.
        final List<Node> evaluatedNodes = session.getEvaluatedNodes();

        //evaluatedNodes.forEach(node -> LOG.info("Evaluated node: {}", node));

        assertEquals(4, evaluatedNodes.size());
        assertEquals(WELCOME_OPT_IN_MESSAGE, evaluatedNodes.get(0).text());
        assertEquals(COLOR_QUIZ_START_TEXT, evaluatedNodes.get(1).text());
        assertEquals(COLOR_QUIZ_UNEXPECTED_INPUT, evaluatedNodes.get(2).text());
        assertEquals(COLOR_QUIZ_END_CONVERSATION, evaluatedNodes.get(3).text());

        assertNull(session.getCurrentNode(), "Session's currentNode is unexpected. Should be null");
    }


    @Test
    void stepThroughWithUnexpectedInputAndChangeTopic() {
        // Preflight check: cache is empty.
        assertEquals(0, operator.scriptByKeywordCache.estimatedSize());

        // Initiate the conversation
        assertDoesNotThrow(() -> {
            operator.process(mo4);
        });

        var session = operator.sessionCache.get(SessionKey.newSessionKey(mo4));
        // Note: this doesn't prove the session was created by the .process() call above.
        assertNotNull(session); // We just need a ref for later checks.
        assertEquals(1, session.getInputHistory().size(), "Wrong number of user inputs.");
        LOG.info("Initial blocking node: {}", session.getCurrentNode());

        // Make sure we get the expected initial response.
        final List<Message> queuedMessages = queueProducer.enqueued();
        queuedMessages.forEach((message -> { println("stepThroughWithUnexpectedInputAndChangeTopic: " + message.text()); }));
        assertEquals(2, queuedMessages.size(), "Unexpected number of messages queued.");
        assertTrue(requireNonNull(queuedMessages.get(1)).text().contains("favorite color"),
                "Unexpected text found in first queued message: " + queuedMessages.get(1).text());
        // The current node should now be awaiting a response
        assertEquals(NodeType.PROCESS_MULTI, session.getCurrentNode().type(), "Session node state has unexpected type.");

        queuedMessages.clear();
        // Now provide bad input to the question posed

        for (int i = 0; i < 3; i++) { // TODO At some point we should add handling for repeated failures. Until then we continue to handle bad input the same way.
            assertDoesNotThrow(() -> { operator.process(unexpected); });
            final Message errorMessage = queuedMessages.get(i);
            assertTrue(errorMessage.text().contains(processAnswer.text()), "Wrong error: " + errorMessage.text());
            // Since we don't advance when there's an error we should still be on the ProcessMulti
            assertEquals(NodeType.PROCESS_MULTI, session.getCurrentNode().type(),
                    "Session node state has unexpected type: " + session.getCurrentNode().type());
        }

        // Current node before interrupt request:
        LOG.info("Session label prior to interrupt: {}", session.getCurrentNode().label());

        // Now request a change of topic
        assertDoesNotThrow(() -> { operator.process(changeTopic); });
        queueProducer.enqueued().forEach(message -> LOG.info("Enqueued: {}", message.text()));

        assertEquals(4, queuedMessages.size(), "Unexpected number of messages queued.");
        assertEquals(processAnswer.text(), queuedMessages.get(1).text(), "Expected text not found in 2nd queued message.");
        assertEquals(processAnswer.text(), queuedMessages.get(2).text(), "Expected text not found in 3rd queued message.");
        assertEquals(Multi.CHANGE_TOPIC_RESPONSE, queuedMessages.get(3).text(),
                "Expected topic change acknowledgement not found in 4th queued message.");

        assertEquals(NodeType.PROCESS_MULTI, session.getCurrentNode().type());
        assertEquals(2, session.getCurrentNode().edges().size());

        session.getCurrentNode().edges().forEach(edge -> {
            LOG.info("Available responses: {}: {} [{}]", edge.responseText(), edge.targetNode().label(), edge.targetNode().type());});

        // FIXME still need to formalize how we know which edge was the placeholder. For now, it's the last one in the list.
        LOG.info("Replaced node: {}", session.getCurrentNode().edges().getLast().targetNode().label());

        assertNotNull(session.getCurrentNode());
    }


    @Test
    void findOrCreateUserUncachedCached() {
        assertDoesNotThrow(() -> {
            var op = new Operator(new FakeQueueConsumer(), new InMemoryQueueProducer(), new TestingPersistenceManager());

            Instant beforeUserCreate = Instant.now(); // use the same timestamp method for comparison.
            assertEquals(0, op.userCache.estimatedSize());

            User uncachedUser = op.userCache.get(SESSION_KEY_US_SHORT_CODE_1);
            assertNotNull(uncachedUser);
            // Make sure this user wasn't previously created.
            final Instant createdAt = uncachedUser.platformCreationTimes().get(Platform.SMS);
            assertTrue(createdAt.isAfter(beforeUserCreate), "before Session create: " + beforeUserCreate + " <= create: " + createdAt);

            User cachedUser = op.userCache.get(SESSION_KEY_US_SHORT_CODE_1);
            assertNotNull(cachedUser);

            // Not just equivalent, the same object
            assertEquals(uncachedUser, cachedUser, "User objects were not equal()");
            assertTrue((uncachedUser == cachedUser), "User objects were the same instance.");
        });
    }


    @Test
    void deriveCountryCodeFromId() {
        assertEquals("CA", Telecom.deriveCountryCodeFromId(MOBILE_CA));
        assertEquals("MX", Telecom.deriveCountryCodeFromId(MOBILE_MX));
        assertEquals("US", Telecom.deriveCountryCodeFromId(MOBILE_US));
    }

}