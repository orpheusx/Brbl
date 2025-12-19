package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.*;
import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.enoughisasgoodasafeast.Message.newMO;
import static com.enoughisasgoodasafeast.Message.newMT;
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
    public static final String SHORT_CODE_4 = "45678";
    public static final String MO_TEXT_1 = "Hello Brbl";
    public static final String MO_TEXT_2 = "Howdy Brbl";
    public static final String SCRIPT_RESPONSE = "script response";

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

    public static final SessionKey SESSION_KEY_US_SHORT_CODE_1 = SessionKey.newSessionKey(mo1);

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
        Node presentQuestion = new Node(COLOR_QUIZ_START_TEXT, NodeType.PresentMulti, "ColorQuizStart");
        processAnswer = new Node(COLOR_QUIZ_UNEXPECTED_INPUT, NodeType.ProcessMulti, "ColorQuizProcessResponse");
        presentQuestion.edges().add(
                new Edge(List.of("n/a"), "n/a", processAnswer)
        );

        endConversation = new Node(COLOR_QUIZ_END_CONVERSATION, NodeType.EndOfChat, "ColorQuizEnd");

        Edge answerRed = new Edge(List.of("red"), "Red is the color of life.", endConversation);
        Edge answerBlue = new Edge(List.of("blue"), "Blue is my fave, as well.", endConversation);
        answerFlort = new Edge(List.of("flort"), "Flort is for the cool kids.", endConversation);

        processAnswer.edges().addAll(List.of(answerRed, answerBlue, answerFlort));

        // Add the test script to the fixture's script "cache."
        ((TestingPersistenceManager) persistenceManager).addScript(
                TestingPersistenceManager.SCRIPT_ID, presentQuestion);

        // Define a default script for the platform-channel, adding it with the default route.
        Node defaultScript = new Node("Welcome! You can talk to us about the following topics...", NodeType.EndOfChat, "CustomerTopicStarter");
        Route route = new Route(Platform.SMS, mo1.to(), defaultScript.id(), UUID.randomUUID());
        operator.activeRoutesCache.put(
                Operator.ALL,
                List.of(route).toArray(new Route[0]));
        // Remember the default routes only holds the script id. We still need to add the script itself to the main cache.
        ((TestingPersistenceManager) persistenceManager).addScript(defaultScript.id(), defaultScript);
    }

    @Test
    void processAndCheckResponse() {

        Node node1 = new Node(SCRIPT_RESPONSE, NodeType.SendMessage);
        Edge edge1 = new Edge(List.of("1", "2", "3"), null);
        node1.edges().add(edge1);

        Message mo1 = newMO(MOBILE_CA, SHORT_CODE_1, MO_TEXT_1);
        Message mo2 = newMO(MOBILE_MX, SHORT_CODE_2, MO_TEXT_1);
        Message mo3 = newMO(MOBILE_US, SHORT_CODE_3, MO_TEXT_2);

        operator.scriptByKeywordCache.put(KeywordCacheKey.newKey(SessionKey.newSessionKey(mo1)), node1);
        operator.scriptByKeywordCache.put(KeywordCacheKey.newKey(SessionKey.newSessionKey(mo2)), node1);
        operator.scriptByKeywordCache.put(KeywordCacheKey.newKey(SessionKey.newSessionKey(mo3)), node1);

        Message mt1 = newMT(SHORT_CODE_1, MOBILE_CA, SCRIPT_RESPONSE);
        Message mt2 = newMT(SHORT_CODE_2, MOBILE_MX, SCRIPT_RESPONSE);
        Message mt3 = newMT(SHORT_CODE_3, MOBILE_US, SCRIPT_RESPONSE);

        assertDoesNotThrow(() -> {
            operator.process(mo1);
        });

        assertEquals(1, queueProducer.enqueuedCount());
        assertEquals(mt1.to(), queueProducer.enqueued().getFirst().to());
        assertEquals(mt1.from(), queueProducer.enqueued().getFirst().from());
        assertEquals(mt1.text(), queueProducer.enqueued().getFirst().text());
        assertEquals(mt1.type(), queueProducer.enqueued().getFirst().type());

        assertDoesNotThrow(() -> { operator.process(mo2); });
        assertEquals(2, queueProducer.enqueuedCount());
        assertEquals(mt2.to(), queueProducer.enqueued().get(1).to());
        assertEquals(mt2.from(), queueProducer.enqueued().get(1).from());
        assertEquals(mt2.text(), queueProducer.enqueued().get(1).text());
        assertEquals(mt2.type(), queueProducer.enqueued().get(1).type());

        assertDoesNotThrow(() -> { operator.process(mo3); });
        assertEquals(3, queueProducer.enqueuedCount());
        assertEquals(mt3.to(), queueProducer.enqueued().get(2).to());
        assertEquals(mt3.from(), queueProducer.enqueued().get(2).from());
        assertEquals(mt3.text(), queueProducer.enqueued().get(2).text());
        assertEquals(mt3.type(), queueProducer.enqueued().get(2).type());
    }

    @Test
    void getUserSessionUncachedCached() {
        Node node1 = new Node(SCRIPT_RESPONSE, NodeType.SendMessage);
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
        assertEquals(s2.getStartTimeNanos(), s3.getStartTimeNanos());
        assertEquals(s2.getUser(), s3.getUser());

        // And are, in fact, the same instance
        assertEquals(s2, s3);

    }

    @Test
    void findMatchForKeyword() {
        final Map<Pattern, Keyword> all = operator.allKeywordsByPatternCache.get(Operator.ALL);
        assertNotNull(all);
        assertFalse(all.isEmpty());

        // NB: MTs don't make sense in this situation. Only MOs and, possibly, push messages.
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
//        ((TestingPersistenceManager) persistenceManager).addScript(
//                TestingPersistenceManager.SCRIPT_ID, presentQuestion);

        // Initiate the conversation
        assertDoesNotThrow(() -> {
            operator.process(mo4);
        });

        // The lookup from scriptCache will have the effect of populating scriptByKeywordCache
        assertEquals(1, operator.scriptByKeywordCache.estimatedSize());

        var session = operator.sessionCache.get(SessionKey.newSessionKey(mo4));
        assertNotNull(session);
        final String userPhoneNumber = session.getUser().platformIds().get(Platform.SMS);
        LOG.info("Session User platform ID = {}", userPhoneNumber);
        assertEquals(MOBILE_MX, userPhoneNumber);

        final List<Message> queuedMessages = queueProducer.enqueued();

        assertEquals(1, queuedMessages.size(), "Unexpected number of messages queued.");
        assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("favorite color"), "Expected text not found in first queued message.");
        assertEquals(NodeType.ProcessMulti, session.getCurrentNode().type(), "Session node state has unexpected type."); // The current node should be awaiting a response

        // Send a valid response
        assertDoesNotThrow(() -> {
            operator.process(mo5); // answer given should select "flort"
        });

        // Check the rest of the MT responses.
        assertEquals(3, queuedMessages.size(), "Unexpected number of messages queued.");
        assertEquals(answerFlort.text(), queuedMessages.get(1).text(), "Expected text not found in 2nd queued message.");
        assertEquals(endConversation.text(), queuedMessages.get(2).text());

        // The conversation is now effectively complete.
        // assertNull(session.getCurrentNode());

        // Check the evaluatedNodes.
        final List<Node> evaluatedNodes = session.getEvaluatedNodes();
        assertEquals(COLOR_QUIZ_UNEXPECTED_INPUT, evaluatedNodes.get(1).text());
        assertEquals(COLOR_QUIZ_END_CONVERSATION, evaluatedNodes.get(2).text());
        assertEquals(COLOR_QUIZ_START_TEXT, evaluatedNodes.get(0).text());

        // Check that the Session's currentNode is now the last node in the conversation
        assertNull(session.currentNode, "Session's currentNode is unexpected.");

        // Instead of null make the last Node to a constant symbolic?
    }


    @Test
    void stepThroughPresentProcessMultiWithBadInput() {

        // Preflight check: cache is empty.
        assertEquals(0, operator.scriptByKeywordCache.estimatedSize());
//        ((TestingPersistenceManager) persistenceManager).addScript(
//                TestingPersistenceManager.SCRIPT_ID, presentQuestion);

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
        assertEquals(1, queuedMessages.size(), "Unexpected number of messages queued.");
        assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("favorite color"), "Expected text not found in first queued message.");
        // The current node should now be awaiting a response
        assertEquals(NodeType.ProcessMulti, session.getCurrentNode().type(), "Session node state has unexpected type.");

        // Now provide bad input to the question posed
        for (int i = 1; i < 3; i++) { // TODO At some point we should add handling for repeated failures. Until then we continue to handle bad input the same way.
            assertDoesNotThrow(() -> { operator.process(unexpected); });
            final Message errorMessage = queuedMessages.get(i);
            LOG.info("Bad input response: {}", errorMessage.text());
            assertTrue(errorMessage.text().contains(processAnswer.text()), "Expected error response not found.");
            // Since we don't advance when there's an error we should still be on the ProcessMulti
            assertEquals(NodeType.ProcessMulti, session.getCurrentNode().type(), "Session node state has unexpected type.");
        }

        // Now provide good input to the question posed and check that we advance to the end of the conversation.
        assertDoesNotThrow(() -> { operator.process(mo5); });
        //producer.enqueued().forEach(message -> LOG.info(message.text()));
        assertEquals(5, queuedMessages.size(), "Unexpected number of messages queued.");
        assertEquals(processAnswer.text(), queuedMessages.get(1).text(), "Expected text not found in 1st queued message.");
        assertEquals(processAnswer.text(), queuedMessages.get(2).text(), "Expected text not found in 2nd queued message.");
        assertEquals(answerFlort.text(), queuedMessages.get(3).text(), "Expected text not found in 3rd queued message.");
        assertEquals(endConversation.text(), queuedMessages.get(4).text());

        // The conversation is complete.

        // Check the evaluatedNodes.
        final List<Node> evaluatedNodes = session.getEvaluatedNodes();

        //evaluatedNodes.forEach(node -> LOG.info("Evaluated node: {}", node));

        assertEquals(3, evaluatedNodes.size());
        assertEquals(COLOR_QUIZ_START_TEXT, evaluatedNodes.get(0).text());
        assertEquals(COLOR_QUIZ_UNEXPECTED_INPUT, evaluatedNodes.get(1).text());
        assertEquals(COLOR_QUIZ_END_CONVERSATION, evaluatedNodes.get(2).text());

        assertNull(session.currentNode, "Session's currentNode is unexpected.");
//        });
    }

    @Test
    void stepThroughWithUnexpectedInputAndChangeTopic() {
        // Preflight check: cache is empty.
        assertEquals(0, operator.scriptByKeywordCache.estimatedSize());
//        ((TestingPersistenceManager) persistenceManager).addScript(
//                TestingPersistenceManager.SCRIPT_ID, presentQuestion);

        // Initiate the conversation
        assertDoesNotThrow(() -> {
            operator.process(mo4);
        });

        var session = operator.sessionCache.get(SessionKey.newSessionKey(mo4));
        assertNotNull(session);

        // Make sure we get the expected initial response.
        final List<Message> queuedMessages = queueProducer.enqueued();
        assertEquals(1, queuedMessages.size(), "Unexpected number of messages queued.");
        assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("favorite color"), "Expected text not found in first queued message.");
        // The current node should now be awaiting a response
        assertEquals(NodeType.ProcessMulti, session.getCurrentNode().type(), "Session node state has unexpected type.");

        // Now provide bad input to the question posed
        for (int i = 1; i <= 2; i++) { // TODO At some point we should add handling for repeated failures. Until then we continue to handle bad input the same way.
            assertDoesNotThrow(() -> { operator.process(unexpected); });
            final Message errorMessage = queuedMessages.get(i);
            assertTrue(errorMessage.text().contains(processAnswer.text()), "Expected error response not found.");
            // Since we don't advance when there's an error we should still be on the ProcessMulti
            assertEquals(NodeType.ProcessMulti, session.getCurrentNode().type(), "Session node state has unexpected type.");
        }

        // Now request a change of topic
        assertDoesNotThrow(() -> { operator.process(changeTopic); });
        queueProducer.enqueued().forEach(message -> LOG.info(message.text()));

        assertEquals(4, queuedMessages.size(), "Unexpected number of messages queued.");
        assertEquals(processAnswer.text(), queuedMessages.get(1).text(), "Expected text not found in 2nd queued message.");
        assertEquals(processAnswer.text(), queuedMessages.get(2).text(), "Expected text not found in 3rd queued message.");
        assertEquals(Multi.CHANGE_TOPIC_RESPONSE, queuedMessages.get(3).text(), "Expected topic change acknowledgement not found in 4th queued message.");

        // At this point we would send a message with a list of available topics...
        // but we haven't yet implemented this functionality.
        // Current thinking is to return a special constant/symbolic Node type that Operator handles by finding the customer's registered topic script

        // The conversation is effectively complete because we've moved to the end of the graph.
        assertNull(session.getCurrentNode());
    }

    @Test
    void findOrCreateUserUncachedCached() {
        assertDoesNotThrow(() -> {
            var op = new Operator(new FakeQueueConsumer(), new InMemoryQueueProducer(), new TestingPersistenceManager());

            Instant beforeUserCreate = NanoClock.utcInstant(); // use the same timestamp method for comparison.
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