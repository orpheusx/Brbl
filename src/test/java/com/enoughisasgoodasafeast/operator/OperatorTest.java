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
    public static final String SHORT_CODE_4 = "4567";
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

    @Test
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

    @Test
    void getUserSessionUncachedCached() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FakeQueueConsumer(), new FileQueueProducer(Paths.get("./target")));
            operator.init();

            assertDoesNotThrow(() -> {
                Session s1 = operator.getUserSession(mo1); // from a US number

                Session s2 = operator.getUserSession(mo2); // from a MX number
                Session s3 = operator.getUserSession(mo3); // from same MX number

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

    @Test
    void findStartingScriptAndStepThroughScript() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FakeQueueConsumer(), new FileQueueProducer(Paths.get("./target")));
            operator.init();

            var session = operator.getUserSession(mo4);

            Script firstScript = operator.findStartingScript(mo4);
            assertNotNull(firstScript, "Failed to return first Script.");
            assertEquals(ScriptType.PresentMulti, firstScript.type());
            assertEquals("ColorQuiz", firstScript.label());

            Script secondScript = firstScript.evaluate(session, mo4);
            System.out.println(secondScript);
            assertEquals(ScriptType.ProcessMulti, secondScript.type());
            session.currentScript = secondScript; // Required! Normally occurs in Operator method, process(Session, Message).

            Script finalScript = secondScript.evaluate(session, mo5);
            System.out.println(finalScript);
            assertEquals(ScriptType.PrintWithPrefix, finalScript.type());
            session.currentScript = finalScript;
        });
    }

    @Test
    void findStartingScriptAndStepThroughScriptWithBadInput() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FakeQueueConsumer(), new InMemoryQueueProducer());
            operator.init();

            var session = operator.getUserSession(mo4);

            Script firstScript = operator.findStartingScript(mo4);

            assertNotNull(firstScript, "Failed to return first Script.");
            assertEquals(ScriptType.PresentMulti, firstScript.type());
            assertEquals("ColorQuiz", firstScript.label());

            Script secondScript = firstScript.evaluate(session, mo4);
            System.out.println(secondScript);
            assertEquals(ScriptType.ProcessMulti, secondScript.type());
            session.currentScript = secondScript; // Required! Normally occurs in Operator method, process(Session, Message).

            Script finalScript = secondScript.evaluate(session, unexpected);
            // An error message should be produced...
            assertTrue(requireNonNull(session.outputBuffer.poll()).text().contains("favorite color"));
            assertTrue(requireNonNull(session.outputBuffer.poll()).text().contains("Oops"));
            // ...but the current script should not have advanced
            assertEquals(ScriptType.ProcessMulti, finalScript.type());
            assertEquals(secondScript, finalScript);
        });
    }

    @Test
    void findStartingScriptAndStepThroughScriptThenChangeTopic() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FakeQueueConsumer(), new FileQueueProducer(Paths.get("./target")));
            operator.init();

            var session = operator.getUserSession(mo4);

            Script firstScript = operator.findStartingScript(mo4);
            assertNotNull(firstScript, "Failed to return first Script.");
            assertEquals(ScriptType.PresentMulti, firstScript.type());
            assertEquals("ColorQuiz", firstScript.label());

            Script secondScript = firstScript.evaluate(session, mo4);
            System.out.println(secondScript);
            assertEquals(ScriptType.ProcessMulti, secondScript.type());
            session.currentScript = secondScript; // Required! Normally occurs in Operator method, process(Session, Message).

            Script thirdScript = secondScript.evaluate(session, unexpected);

            // An error message should be produced...
            assertTrue(requireNonNull(session.outputBuffer.poll()).text().contains("favorite color"));
            assertTrue(requireNonNull(session.outputBuffer.poll()).text().contains("Oops"));

            // ...but the current script should not have advanced
            assertEquals(ScriptType.ProcessMulti, thirdScript.type());
            assertEquals(secondScript, thirdScript);
            session.currentScript = thirdScript;

            assertEquals(0, session.outputBuffer.size());

            Script fourthScript = thirdScript.evaluate(session, changeTopic);
            assertEquals("e-o-c", fourthScript.label());
            assertEquals(ScriptType.ProcessMulti, fourthScript.type());
            session.currentScript = fourthScript;
            assertEquals(2, session.outputBuffer.size());

            assertTrue(requireNonNull(session.outputBuffer.poll()).text().contains("something else"));
            assertTrue(requireNonNull(session.outputBuffer.poll()).text().contains("monetary"));

            assertEquals(0, session.outputBuffer.size());

            Script finalScript = fourthScript.evaluate(session, wolverine);
            System.out.println(session.outputBuffer);
            assertTrue(session.outputBuffer.poll().text().contains("pointy teeth"));
        });
    }

    @Test
    void stepThroughPresentProcessMulti() {
        assertDoesNotThrow(() -> {
            var producer = new InMemoryQueueProducer();
            var operator = new Operator(new FakeQueueConsumer(), producer);
            operator.init();

            assertTrue(operator.process(mo4));
            var session = operator.getUserSession(mo4);
            final List<Message> queuedMessages = producer.getQueuedMessages();
            assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("favorite color"));
            assertEquals(ScriptType.ProcessMulti, session.currentScript().type());

            assertTrue(operator.process(mo5));
            assertTrue(requireNonNull(queuedMessages.get(1)).text().contains("cool kids"));
            assertEquals(ScriptType.PrintWithPrefix, session.currentScript().type());

        });
    }

    @Test
    void stepThroughWithUnexpectedInputAndChangeTopic() {
        assertDoesNotThrow(() -> {
            var producer = new InMemoryQueueProducer();
            var operator = new Operator(new FakeQueueConsumer(), producer);
            var queuedMessages = producer.getQueuedMessages();
            operator.init();

            assertTrue(operator.process(mo4));

            var session = operator.getUserSession(mo4);
            assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("favorite color"));
            queuedMessages.clear();

            Script faveColorScript = session.currentScript();
            assertEquals(ScriptType.ProcessMulti, faveColorScript.type());

            assertTrue(operator.process(unexpected));
            // An error message should have been produced...
            assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("Oops"));
            queuedMessages.clear();

            Script stillFaveColorScript = session.currentScript();
            assertEquals(ScriptType.ProcessMulti, faveColorScript.type());
            assertEquals(faveColorScript, stillFaveColorScript);

            assertEquals(0, session.outputBuffer.size());

            assertTrue(operator.process(changeTopic)); // will emit a notice message and the topic display message
            assertEquals(2, queuedMessages.size());
            Script changeTopicScript = session.currentScript();
            assertEquals(ScriptType.ProcessMulti, changeTopicScript.type());

            assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("something else"));
            assertTrue(requireNonNull(queuedMessages.get(1)).text().contains("monetary"));
            queuedMessages.clear();

            assertTrue(operator.process(wolverine));
            assertEquals(ScriptType.ProcessMulti, session.currentScript().type());
            assertEquals(1, queuedMessages.size());
            assertTrue(requireNonNull(queuedMessages.getFirst()).text().contains("pointy teeth"));
        });
    }

    @Test
    void findOrCreateUserUncachedCached() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FakeQueueConsumer(), new FileQueueProducer(Paths.get("./target")));
            operator.init();

            User uncachedUser = operator.findOrCreateUser(MOBILE_US, SHORT_CODE_1);
            assertNotNull(uncachedUser);

            User cachedUser = operator.findOrCreateUser(MOBILE_US, SHORT_CODE_1);
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