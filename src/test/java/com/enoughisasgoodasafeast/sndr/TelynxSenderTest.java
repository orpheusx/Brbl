package com.enoughisasgoodasafeast.sndr;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.MessageType;
import com.enoughisasgoodasafeast.operator.TestingPersistenceManager;
import org.junit.jupiter.api.Test;

public class TelynxSenderTest {

    @Test
    public void sendMessage() {
        var tpm = new TestingPersistenceManager();
        var sender = new TelnyxSender(tpm);
        // send a message
        var testMessage = new Message(MessageType.MT, "+19788879704", "+17817209468", "A test of the Telnyx gateway service.");
        sender.send(testMessage);
    }
}
