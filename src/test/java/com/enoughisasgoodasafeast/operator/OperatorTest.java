package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.FakeQueueConsumer;
import com.enoughisasgoodasafeast.FileQueueProducer;
import com.enoughisasgoodasafeast.InMemoryQueueProducer;
import com.enoughisasgoodasafeast.Message;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static com.enoughisasgoodasafeast.Message.newMO;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

public class OperatorTest {

    public static final String MOBILE_CA = "14385551234";  // Quebec, CA
    public static final String MOBILE_MX = "522005551234"; // Mexico City, MX
    public static final String MOBILE_US = "17815551234";  // Massachusetts, US
    public static final String SHORT_CODE_1 = "1234";
    public static final String SHORT_CODE_2 = "2345";
    public static final String SHORT_CODE_3 = "3456";
    public static final String SHORT_CODE_4 = "45678";
    public static final String MO_TEXT = "Hello Brbl";

    public static final Message mo1 = newMO(
            MOBILE_US, SHORT_CODE_1, MO_TEXT
    );
    public static final Message mo2 = newMO(
            MOBILE_MX, SHORT_CODE_1, MO_TEXT
    );
    public static final Message mo3 = newMO(
            MOBILE_MX, SHORT_CODE_1, "Adiós Brbl"
    );
    public static final Message mo4 = newMO(
            MOBILE_MX, SHORT_CODE_4, "Color quiz"
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

    //    @Test
    void processWithFileQueueProducer() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FakeQueueConsumer(), new FileQueueProducer(Paths.get("./target")));
            operator.init();

            Message message1 = newMO(MOBILE_CA, SHORT_CODE_1, "Testing the process method.");
            Message message2 = newMO(MOBILE_MX, SHORT_CODE_2, "Reverse this text");
            Message message3 = newMO(MOBILE_US, SHORT_CODE_3, "42 hello");
            assertDoesNotThrow(() -> {
                operator.init();
                assertTrue(operator.process(message1));
                assertTrue(operator.process(message2));
                assertTrue(operator.process(message3));
            });

            Message message4 = newMO(MOBILE_CA, SHORT_CODE_4, "one");
            Message message5 = newMO(MOBILE_CA, SHORT_CODE_4, "two");
            Message message6 = newMO(MOBILE_CA, SHORT_CODE_4, "23 hello");
            assertDoesNotThrow(() -> {
                assertTrue(operator.process(message4));
                assertTrue(operator.process(message5));
                assertTrue(operator.process(message6));
            });
        });
    }

//    @Test
    void getUserSessionUncachedCached() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FakeQueueConsumer(), new FileQueueProducer(Paths.get("./target")));
            operator.init();

            assertDoesNotThrow(() -> {
                Session s1 = operator.sessionCache.get(SessionKey.newSessionKey(mo1)); // from a US number

                Session s2 = operator.sessionCache.get(SessionKey.newSessionKey(mo2)); // from a MX number
                Session s3 = operator.sessionCache.get(SessionKey.newSessionKey(mo3)); // from same MX number

                // Sessions for two different Users are separate
                assertNotEquals(s1.id, s2.id);

                // A Session, once cached, is returned for subsequent messages from the same User
                assertEquals(s2.id, s3.id);
                // and has the same values (records guarantee this but...)
                assertEquals(s2.startTimeNanos, s3.startTimeNanos);
                assertEquals(s2.user, s3.user);
            });
        });
    }

//    @Test
    void findStartingScriptAndStepThroughScript() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FakeQueueConsumer(), new FileQueueProducer(Paths.get("./target")));
            operator.init();

            var mo4SessionKey = SessionKey.newSessionKey(mo4);
            var session = operator.sessionCache.get(mo4SessionKey);

            Node firstNode = operator.findStartingScript(mo4SessionKey);
            assertNotNull(firstNode, "Failed to return first Node.");
            assertEquals(NodeType.PresentMulti, firstNode.type());
            assertEquals("ColorQuiz", firstNode.label());

            Node secondNode = firstNode.evaluate(session, mo4);
            System.out.println(secondNode);
            assertEquals(NodeType.ProcessMulti, secondNode.type());
            session.currentNode = secondNode; // Required! Normally occurs in Operator method, process(Session, Message).

            Node finalNode = secondNode.evaluate(session, mo5);
            System.out.println(finalNode);
            assertEquals(NodeType.EchoWithPrefix, finalNode.type());
            session.currentNode = finalNode;
        });
    }

//    @Test
    void findStartingScriptAndStepThroughScriptWithBadInput() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FakeQueueConsumer(), new InMemoryQueueProducer());
            operator.init();

            var session = operator.sessionCache.get(SessionKey.newSessionKey(mo4));

            SessionKey mo4SessionKey = SessionKey.newSessionKey(mo4);
            Node firstNode = operator.findStartingScript(mo4SessionKey);

            assertNotNull(firstNode, "Failed to return first Node.");
            assertEquals(NodeType.PresentMulti, firstNode.type());
            assertEquals("ColorQuiz", firstNode.label());

            Node secondNode = firstNode.evaluate(session, mo4);
            System.out.println(secondNode);
            assertEquals(NodeType.ProcessMulti, secondNode.type());
            session.currentNode = secondNode; // Required! Normally occurs in Operator method, process(Session, Message).

            Node finalNode = secondNode.evaluate(session, unexpected);
            // An error message should be produced...
            assertTrue(requireNonNull(session.getOutputBuffer().poll()).text().contains("favorite color"));
            assertTrue(requireNonNull(session.getOutputBuffer().poll()).text().contains("Oops"));
            // ...but the current node should not have advanced
            assertEquals(NodeType.ProcessMulti, finalNode.type());
            assertEquals(secondNode, finalNode);
        });
    }

//    @Test
    void findStartingScriptAndStepThroughScriptThenChangeTopic() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FakeQueueConsumer(), new FileQueueProducer(Paths.get("./target")));
            operator.init();

            SessionKey mo4SessionKey = SessionKey.newSessionKey(mo4);
            var session = operator.sessionCache.get(mo4SessionKey);
            assertNotNull(session);

            Node firstNode = operator.findStartingScript(mo4SessionKey);
            assertNotNull(firstNode, "Failed to return first Node.");
            assertEquals(NodeType.PresentMulti, firstNode.type());
            assertEquals("ColorQuiz", firstNode.label());

            Node secondNode = firstNode.evaluate(session, mo4);
            System.out.println(secondNode);
            assertEquals(NodeType.ProcessMulti, secondNode.type());
            session.currentNode = secondNode; // Required! Normally occurs in Operator method, process(Session, Message).

            Node thirdNode = secondNode.evaluate(session, unexpected);

            // An error message should be produced...
            assertTrue(requireNonNull(session.getOutputBuffer().poll()).text().contains("favorite color"));
            assertTrue(requireNonNull(session.getOutputBuffer().poll()).text().contains("Oops"));

            // ...but the current node should not have advanced
            assertEquals(NodeType.ProcessMulti, thirdNode.type());
            assertEquals(secondNode, thirdNode);
            session.currentNode = thirdNode;

            assertEquals(0, session.getOutputBuffer().size());

            Node fourthNode = thirdNode.evaluate(session, changeTopic);
            assertEquals("e-o-c", fourthNode.label());
            assertEquals(NodeType.ProcessMulti, fourthNode.type());
            session.currentNode = fourthNode;
            assertEquals(2, session.getOutputBuffer().size());

            assertTrue(requireNonNull(session.getOutputBuffer().poll()).text().contains("something else"));
            assertTrue(requireNonNull(session.getOutputBuffer().poll()).text().contains("monetary"));

            assertEquals(0, session.getOutputBuffer().size());

            Node finalNode = fourthNode.evaluate(session, wolverine);
            System.out.println(session.getOutputBuffer());
            assertTrue(session.getOutputBuffer().poll().text().contains("pointy teeth"));
        });
    }

//    @Test
    void stepThroughPresentProcessMulti() {
        assertDoesNotThrow(() -> {
            var producer = new InMemoryQueueProducer();
            var operator = new Operator(new FakeQueueConsumer(), producer);
            operator.init();

            assertTrue(operator.process(mo4));
            var session = operator.sessionCache.get(SessionKey.newSessionKey(mo4));
            final List<Message> queuedMessages = producer.getQueuedMessages();
            assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("favorite color"));
            assertEquals(NodeType.ProcessMulti, session.getCurrentScript().type());

            assertTrue(operator.process(mo5));
            assertTrue(requireNonNull(queuedMessages.get(1)).text().contains("cool kids"));
            assertEquals(NodeType.EchoWithPrefix, session.getCurrentScript().type());

        });
    }

//    @Test
    void stepThroughWithUnexpectedInputAndChangeTopic() {
        assertDoesNotThrow(() -> {
            var producer = new InMemoryQueueProducer();
            var operator = new Operator(new FakeQueueConsumer(), producer);
            var queuedMessages = producer.getQueuedMessages();
            operator.init();

            assertTrue(operator.process(mo4));

            var session = operator.sessionCache.get(SessionKey.newSessionKey(mo4));
            assertNotNull(session);
            assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("favorite color"));
            queuedMessages.clear();

            Node faveColorNode = session.getCurrentScript();
            assertEquals(NodeType.ProcessMulti, faveColorNode.type());

            assertTrue(operator.process(unexpected));
            // An error message should have been produced...
            assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("Oops"));
            queuedMessages.clear();

            Node stillFaveColorNode = session.getCurrentScript();
            assertEquals(NodeType.ProcessMulti, faveColorNode.type());
            assertEquals(faveColorNode, stillFaveColorNode);

            assertEquals(0, session.getOutputBuffer().size());

            assertTrue(operator.process(changeTopic)); // will emit a notice message and the topic display message
            assertEquals(2, queuedMessages.size());
            Node changeTopicNode = session.getCurrentScript();
            assertEquals(NodeType.ProcessMulti, changeTopicNode.type());

            assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("something else"));
            assertTrue(requireNonNull(queuedMessages.get(1)).text().contains("monetary"));
            queuedMessages.clear();

            assertTrue(operator.process(wolverine));
            assertEquals(NodeType.ProcessMulti, session.getCurrentScript().type());
            assertEquals(1, queuedMessages.size());
            assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("pointy teeth"));
        });
    }

//    @Test
    void findOrCreateUserUncachedCached() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FakeQueueConsumer(), new FileQueueProducer(Paths.get("./target")));
            operator.init();


            User uncachedUser = operator.findOrCreateUser(SESSION_KEY_US_SHORT_CODE_1);
            assertNotNull(uncachedUser);

            User cachedUser = operator.findOrCreateUser(SESSION_KEY_US_SHORT_CODE_1);
            assertNotNull(cachedUser);

            // Not just equivalent, the same object
            assertTrue((uncachedUser == cachedUser));
        });
    }

    @Test
    void deriveCountryCodeFromId() {
        assertEquals("CA", Telecom.deriveCountryCodeFromId(MOBILE_CA));
        assertEquals("MX", Telecom.deriveCountryCodeFromId(MOBILE_MX));
        assertEquals("US", Telecom.deriveCountryCodeFromId(MOBILE_US));
    }

}