package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.InMemoryQueueProducer;
import com.enoughisasgoodasafeast.Message;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.enoughisasgoodasafeast.operator.ScriptType.*;
import static com.enoughisasgoodasafeast.operator.Session.MAX_INPUT_HISTORY;
import static com.enoughisasgoodasafeast.operator.UserTest.platformIds;
import static com.enoughisasgoodasafeast.operator.UserTest.countryCode;
import static com.enoughisasgoodasafeast.operator.UserTest.languages;
import static org.junit.jupiter.api.Assertions.*;

class SessionTest {

    @Test
    void maintainInputHistorySize() {
        String FROM = "11234567890";
        String TO = "12345";
        Session session = new Session(
                UUID.randomUUID(),
                new Script("do nothing", EchoWithPrefix, null),
                new User(UUID.randomUUID(), platformIds, countryCode, languages),
                new InMemoryQueueProducer(),
                null);
        int numElements = MAX_INPUT_HISTORY + 1;
        for (int i = 0; i < numElements; i++) {
            Message mo = Message.newMO(FROM, TO, String.valueOf(i));
            session.inputHistory.addLast(mo);
        }
        assertEquals(MAX_INPUT_HISTORY, session.inputHistory.size());
        assertEquals("1", session.inputHistory.getFirst().text());
        assertEquals("10", session.inputHistory.getLast().text());
    }
}