package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.InMemoryQueueProducer;
import com.enoughisasgoodasafeast.Message;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.enoughisasgoodasafeast.operator.ScriptType.*;
import static com.enoughisasgoodasafeast.operator.Session.MAX_INPUT_HISTORY;
import static com.enoughisasgoodasafeast.operator.UserTest.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionTest {

    @Test
    void maintainInputHistorySize() {
        assertDoesNotThrow(() -> {
            String FROM = "11234567890";
            String TO = "12345";
            Session session = new Session(
                    UUID.randomUUID(),
                    new Script("do nothing", EchoWithPrefix, null),
                    new User(UUID.randomUUID(), platformIds, platformsCreated, countryCode, languages),
                    new InMemoryQueueProducer(),
                    null);
            int numElements = MAX_INPUT_HISTORY + 1;
            for (int i = 0; i < numElements; i++) {
                Message mo = Message.newMO(FROM, TO, String.valueOf(i));
                session.registerInput(mo);
            }
            session.flush(); // adds all the inputs to the inputHistory

            assertEquals(MAX_INPUT_HISTORY, session.getInputHistory().size());
            assertEquals("1", session.getInputHistory().getFirst().text());
            assertEquals("10", session.getInputHistory().getLast().text());
        });
    }
}