package com.enoughisasgoodasafeast.sndr;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.MessageType;
import com.enoughisasgoodasafeast.operator.Platform;
import com.enoughisasgoodasafeast.operator.Route;
import com.enoughisasgoodasafeast.operator.TestingPersistenceManager;
import com.enoughisasgoodasafeast.sndr.sim.server.TelnyxServerMain;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static com.enoughisasgoodasafeast.RetryDelayRoutingKey.*;
import static com.enoughisasgoodasafeast.operator.ProcessState.*;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TelynxSenderTest {

    private static final Logger LOG = LoggerFactory.getLogger(TelynxSenderTest.class);

    private static final String ROUTE_CHANNEL = "+17814567890";

    private static WebServer server;
    private TelnyxSender sender;

    @BeforeAll
    public static void startServerSim() {
        server = TelnyxServerMain.startServer();
        LOG.info("Started TelynxServer.");
    }

    @AfterAll
    public static void stopServerSim() {
        server.stop();
        LOG.info("Stopped TelynxServer.");
    }

    @BeforeEach
    public void setUp() {
        var tpm = new TestingPersistenceManager();
        var routes = new Route[]{
                // Use default values for expiration and retry limits.
                new Route(Platform.SMS, ROUTE_CHANNEL, UUID.randomUUID(), randomUUID(), randomUUID(), randomUUID(), randomUUID())
        };
        tpm.setActiveRoutes(routes);
        sender = new TelnyxSender(tpm);
    }

    @Test
    public void sendMessage() {
        LOG.info("Running sendMessage");
        // Send a correctly formatted message
        var testMessage = new Message(
                MessageType.MT, ROUTE_CHANNEL, "+17817209468", "A test of the Telnyx gateway service.");
        final var stateRoutingKey = sender.send(testMessage);
        assertSame(OK, stateRoutingKey.processState());
    }

    @Test
    public void sendMessageWithMalformedToFrom() {
        // Send a message with phone numbers that are non E.164 compliant.
        var testMessage = new Message(
                MessageType.MT, "19788879704", "+1-781-720-9468", "Malformed to and from fields.");
        final var stateRoutingKey = sender.send(testMessage);
        assertSame(ERROR, stateRoutingKey.processState());
    }

    @Test
    public void sendMessageExpectThrottle() {
        var signal429ResponseMessage = new Message(
                MessageType.MT, ROUTE_CHANNEL, "+17817209468", "429:too-many:retry-after");
        final var stateRoutingKey = sender.send(signal429ResponseMessage);
        assertSame(RETRY, stateRoutingKey.processState());
        assertSame(DELAY_5S, stateRoutingKey.retryDelayRoutingKey());
    }

    @Test
    public void convertLogic() {
        assertSame(DELAY_5S, sender.convert(1));
        assertSame(DELAY_5S, sender.convert(4));
        assertSame(DELAY_5S, sender.convert(5));
        assertSame(DELAY_10S, sender.convert(9));
        assertSame(DELAY_30S, sender.convert(15));
        assertSame(DELAY_1M, sender.convert(59));
        assertSame(DELAY_1M, sender.convert(60));
        assertSame(DELAY_2M, sender.convert(61));
        assertSame(DELAY_5M, sender.convert(121));
        // We don't have a 30m routing key so use our largest available.
        assertSame(DELAY_20M, sender.convert(1_800));
    }
}
