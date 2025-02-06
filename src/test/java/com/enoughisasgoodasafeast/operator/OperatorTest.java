package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.MOMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OperatorTest {

    public static final String MOBILE_CA = "14385551234";  // Quebec, CA
    public static final String MOBILE_MX = "522005551234"; // Mexico City, MX
    public static final String MOBILE_US = "17815551234";  // Massachusetts, US
    public static final String SHORT_CODE = "1234";
    public static final String MO_TEXT = "Hello Brbl";
    public static final MOMessage mo1 = new MOMessage(
            MOBILE_US, SHORT_CODE, MO_TEXT
    );
    public static final MOMessage mo2 = new MOMessage(
            MOBILE_MX, SHORT_CODE, MO_TEXT
    );
    public static final MOMessage mo3 = new MOMessage(
            MOBILE_MX, SHORT_CODE, "Adiós Brbl"
    );

//    @Test
//    void process() {
//    }

    @Test
    void process() {
        MOMessage message = new MOMessage(MOBILE_CA, SHORT_CODE, "Testing the process method.");
        var operator = new Operator();
        assertDoesNotThrow(() -> {
            operator.init();
            assertTrue(operator.process(message));
        });
    }

    @Test
    void getUserSessionUncachedCached() {
        var operator = new Operator();

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
    }

    @Test
    void findOrCreateUserUncachedCached() {
        var operator = new Operator();
        User uncachedUser = operator.findOrCreateUser(MOBILE_US, SHORT_CODE);
        assertNotNull(uncachedUser);

        User cachedUser = operator.findOrCreateUser(MOBILE_US, SHORT_CODE);
        assertNotNull(cachedUser);

        // Not just equivalent, the same object
        assertTrue((uncachedUser==cachedUser));
    }

    @Test
    void deriveCountryCodeFromId() {
        assertEquals("CA", Telecom.deriveCountryCodeFromId(MOBILE_CA));
        assertEquals("MX", Telecom.deriveCountryCodeFromId(MOBILE_MX));
        assertEquals("US", Telecom.deriveCountryCodeFromId(MOBILE_US));
    }
}