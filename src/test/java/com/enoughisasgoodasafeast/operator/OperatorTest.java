package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.FileQueueProducer;
import com.enoughisasgoodasafeast.MOMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

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
    public static final MOMessage mo1 = new MOMessage(
            MOBILE_US, SHORT_CODE_1, MO_TEXT
    );
    public static final MOMessage mo2 = new MOMessage(
            MOBILE_MX, SHORT_CODE_1, MO_TEXT
    );
    public static final MOMessage mo3 = new MOMessage(
            MOBILE_MX, SHORT_CODE_1, "Adiós Brbl"
    );
    public static final MOMessage mo4 = new MOMessage(
            MOBILE_MX, SHORT_CODE_4, "Color quiz"
    );
    public static final MOMessage mo5 = new MOMessage(
            MOBILE_MX, SHORT_CODE_4, "Flort"
    );

//    @Test
//    void process() {
//    }

    @Test
    void processWithFileQueueProducer() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FileQueueProducer(Paths.get("./target")));

            MOMessage message1 = new MOMessage(MOBILE_CA, SHORT_CODE_1, "Testing the process method.");
            MOMessage message2 = new MOMessage(MOBILE_MX, SHORT_CODE_2, "Reverse this text");
            MOMessage message3 = new MOMessage(MOBILE_US, SHORT_CODE_3, "42 hello");
            assertDoesNotThrow(() -> {
                operator.init();
                assertTrue(operator.process(message1));
                assertTrue(operator.process(message2));
                assertTrue(operator.process(message3));
            });

            MOMessage message4 = new MOMessage(MOBILE_CA, SHORT_CODE_4, "one");
            MOMessage message5 = new MOMessage(MOBILE_CA, SHORT_CODE_4, "two");
            MOMessage message6 = new MOMessage(MOBILE_CA, SHORT_CODE_4, "23 hello");
            assertDoesNotThrow(() -> {
                assertTrue(operator.process(message4));
                assertTrue(operator.process(message5));
                assertTrue(operator.process(message6));
            });
        });
    }

    @Test
    void processWithExistingSession() {
    }

    @Test
    void getUserSessionUncachedCached() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FileQueueProducer(Paths.get("./target")));

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
    void findStartingScriptAndStepThrough() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FileQueueProducer(Paths.get("./target")));
            var session = operator.getUserSession(mo4);

            Script firstScript = operator.findStartingScript(mo4);
            assertNotNull(firstScript, "Failed to return first Script.");
            assertEquals(ScriptType.PresentMulti, firstScript.type());
            assertEquals("ColorQuiz", firstScript.label());

            Script secondScript = firstScript.evaluate(session, mo4);
            System.out.println(secondScript);
            assertEquals(ScriptType.ProcessMulti, secondScript.type());
            session.currentScript = secondScript; // Required! Normally occurs in Operator method, process(Session, MOMessage).

            Script finalScript = secondScript.evaluate(session, mo5);
            System.out.println(finalScript);
            assertEquals(ScriptType.PrintWithPrefix, finalScript.type());
            session.currentScript = finalScript;

        });
    }

    @Test
    void findOrCreateUserUncachedCached() {
        assertDoesNotThrow(() -> {
            var operator = new Operator(new FileQueueProducer(Paths.get("./target")));
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