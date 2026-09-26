package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.MessageType;
import com.enoughisasgoodasafeast.operator.TestingPersistenceManager;
import com.enoughisasgoodasafeast.sndr.RouteInfo;
import com.enoughisasgoodasafeast.sndr.TelnyxSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TelnyxSenderConnectionTest {
    private static final Logger LOG = LoggerFactory.getLogger(TelnyxSenderConnectionTest.class);

    static final String senderChannel = "+15122345678";
    static final String mtReceiver = "+17812234533";
    static final Message mtMessage = new Message(MessageType.MT, senderChannel, mtReceiver, "text");

    TestingPersistenceManager persistenceManager;
    TelnyxSender telnyxSender;

    @BeforeEach
    void beforeEach() {
        persistenceManager = new TestingPersistenceManager();
        telnyxSender = new TelnyxSender(persistenceManager);
    }

    @Test
    void unreachableEndpoint() {
        assertThrows(RuntimeException.class, () -> {
            var psrk = telnyxSender.send(mtMessage,
                    new RouteInfo("fake_provider_id", "fake_auth", randomUUID(), senderChannel));
            LOG.error("Expected RuntimeException but got: {}", psrk);
        });
    }

}
