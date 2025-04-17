package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionKeyTest {

    @Test
    void newSessionKey() {

        Message message = Message.newMO("1234567890", "12345", "a message");
        SessionKey sessionKey1 = SessionKey.newSessionKey(message);
        SessionKey sessionKey2 = SessionKey.newSessionKey(message);

        assertTrue(sessionKey1.equals(sessionKey2));
        assertTrue(sessionKey1.hashCode()==sessionKey2.hashCode());
    }
}