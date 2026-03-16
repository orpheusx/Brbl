package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.operator.*;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.enoughisasgoodasafeast.MessageType.MO;
import static com.enoughisasgoodasafeast.MessageType.MT;
import static com.enoughisasgoodasafeast.SharedConstants.*;
import static io.helidon.http.HeaderValues.CONTENT_TYPE_TEXT_PLAIN;

// TODO add a ping/health call at start up to verify our MO endpoint (aka Rcvr) is running. Do same for Sndr?
public class ChttrClient {

    private static final Logger LOG = LoggerFactory.getLogger(ChttrClient.class);
    private final Map<String, UserActor> userActors = new ConcurrentHashMap<>();
    private final MOHandler moHandler;
    private final Properties properties;

    List<String> deserializationErrors = new ArrayList<>();
    List<String> unknownUserActor = new ArrayList<>();

    private final AtomicInteger countdown;

    public ChttrClient(Properties properties, List<UserActor> actors) {
        this.properties = properties;
        for (UserActor actor : actors) {
            userActors.put(actor.getPhoneNumber(), actor);
        }
        this.moHandler = HttpMOHandler.newHandler(properties);
        this.countdown = new AtomicInteger(actors.size());
    }

    public void start() {
        int webServerPort = Integer.parseInt(properties.getProperty("chttr.listener.port"));
        LOG.info("ChttrClient listening on port {}", webServerPort);

        WebServer.builder()
                .port(webServerPort)
                .connectionConfig(config -> {
                            config.connectTimeout(Duration.of(CONNECTION_TIMEOUT_SECONDS, ChronoUnit.SECONDS));
                            config.keepAlive(true);
                        }
                )
                .routing(router -> {
                            router.post(CHTTR_SERVICE_ENDPOINT, new ChttrMessageHandler());
                            router.get(START_CHTTR_SERVICE_ENDPOINT, new ExecuteTestHandler());
                        }
                )
                .build()
                .start();
    }

    private class ExecuteTestHandler implements Handler {
        // ref:  curl -d '18484242144:119839196677:foo' http://192.168.1.155:4242/smsEnqueue
        // TODO accept the keyword and and _to_ field (aka the channel) as query args

        @Override
        public void handle(ServerRequest request, ServerResponse response) {
            LOG.info("ChttrClient executing tests...");
            response.send("OK");

            String keyword = "foo"; // FIXME
            for (UserActor actor : userActors.values()) {
                var startMessage = new Message(MO, Platform.SMS, actor.getPhoneNumber(), "119839196677", keyword);
                LOG.info("Sending initiating keyword '{}' from user number, {}", keyword, actor.getPhoneNumber());
                final boolean ok = moHandler.handle(startMessage);
                if (!ok) {
                    LOG.error("Failed to send initiating keyword, {}, from {}", keyword, actor.getPhoneNumber());
                }
            }
        }
    }

    // TODO Modify the way we did for Rcvr; separate handle for each supported Platform.
    private class ChttrMessageHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            res.header(CONTENT_TYPE_TEXT_PLAIN);
            String rcvPayload = req.content().as(String.class);
            LOG.info("Handling incoming message: {}", rcvPayload);

            Message mtMessage = marshall(rcvPayload);
            if (mtMessage != null) {
                processMessage(mtMessage);
            } else {
                res.send("FAIL"); // res.send(Status.BAD_REQUEST_400);
            }

            res.send("OK"); // res.send(Status.OK_200);
        }

        private @Nullable Message marshall(@NonNull String payload) {
            // Sndr uses HttpMessageHandler which sends "from:to:text".
            String[] parsed = payload.split(":", 3);
            if (parsed.length < 3) {
                 LOG.error("Invalid message format: {}", payload);
                 deserializationErrors.add(payload);
                 return null;
            }
            // In MT message, 'to' is the user's phone number.
            return new Message(MT, Platform.SMS, parsed[0], parsed[1], parsed[2]);// FIXME Platform cannot be hardcoded!!!
        }

        @Override
        public void afterStop() {
            //generateRunReport();
            LOG.info("ChttrClient stopping...");
        }
    }

    private void generateRunReport() {
        // Docker Compose appears to shut off logging output before this gets called.
        // Inspecting the container's logs directly, however, proves that is getting called.
        LOG.info("Generating run report...");
        LOG.info("ChttrClient final stats:");
        int numActors=0, numRcvd=0, numSent=0;
        for (var entry : userActors.entrySet()) {
            ++numActors;
            final var phoneNumber = entry.getKey();
            final var userActor = entry.getValue();
            numRcvd += userActor.rcvdMessages.size();
            numSent += userActor.sentMessages.size();
            LOG.info("UserActor: {}\n\t rcvd: {} \n\t sent: {}", phoneNumber, userActor.rcvdMessages, userActor.sentMessages);
        }
        LOG.info("Totals: numActors: {}, numRcvd: {}, numSent: {}", numActors, numRcvd, numSent);

    }

    private void processMessage(@NonNull Message mtIncomingMessage) {
        String phoneNumber = mtIncomingMessage.to(); // The user is the recipient of the MT message
        UserActor actor = userActors.get(phoneNumber);

        if (actor == null) {
            LOG.warn("No matching UserActor found for phone number: {}", phoneNumber);
            unknownUserActor.add(phoneNumber);
            return;
        } else {
            actor.rcvdMessages.add(mtIncomingMessage);
        }

        Event event = actor.currentEvent();
        if(event==null) { // A null event here indicates we've completed execution of all the ChttrScripts for actor.
            LOG.info("Ignoring incoming message '{}'", mtIncomingMessage.text());
            if(complete()) {
                LOG.info("All actors have completed.");
                generateRunReport();
            }

            return;
        }

        if (!MT.equals(event.type())) { // FIXME This doesn't really make sense. Operator can only send MTs.
            LOG.error("Script expects event of type, {}", event.type().toString());
            return;
        }

        if (!event.message().equals(mtIncomingMessage.text())) {
            actor.recordUnexpectedMessage(mtIncomingMessage.text());
            LOG.error("Script expects '{}' but got '{}'", event.message(), mtIncomingMessage.text());
            return;
        } else {
            LOG.info("User {} matched expected message: {}", actor.getPhoneNumber(), event.message());
        }

        // Advance to the next event.
        Event nextEvent = actor.nextEvent();
        // If it's an MO we should send it as a response, continuing until we find a non-MO event.
        while(nextEvent != null && nextEvent.type() == MO) {
            // Create MO message. 'from' is the user (phoneNumber), 'to' is the sender of the MT message.
            Message moResponseMessage = new Message(MO, mtIncomingMessage.platform(), phoneNumber, mtIncomingMessage.from(),
                    nextEvent.message());
            boolean success = moHandler.handle(moResponseMessage);
            if (success) {
                LOG.info("Response sent from {} with {}", phoneNumber, moResponseMessage);
                actor.sentMessages.add(moResponseMessage);
                nextEvent = actor.nextEvent();
            } else {
                actor.recordSendMessageFail(moResponseMessage.text());
                LOG.error("Response fail from {} with {}", phoneNumber, moResponseMessage);
                return;
            }
        }
    }

    private boolean complete() {
        return 0 == countdown.decrementAndGet();
    }

//    private @Nullable Exchange findMatchingExchange(@NonNull UserActor actor, @NonNull String text) {
//        // Gemini wrote this functional way of finding a match. Looks cool though a regular loop is more efficient for smaller list (<1k) like this one. :-[
//        return actor.getScript().exchanges.stream()
//                .filter(e -> e.mtText().equals(text))
//                .findFirst()
//                .orElse(null);
//    }

    public static void main(String[] args) {
        UUID pushCampaignId, nodeId;
        try {
            pushCampaignId = UUID.fromString(args[1]);
            nodeId = UUID.fromString(args[2]);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid UUID format.");
            return;
        }

        try {
            var properties = ConfigLoader.readConfig("chttr.properties"); // Assuming a config file

            var persistenceManager = PostgresPersistenceManager.createPersistenceManager(properties);

            Collection<CampaignUser> campaignUsers = persistenceManager.getPushCampaignUsers(pushCampaignId, DeliveryStatus.PENDING);
            LOG.info("Found {} campaign users for campaign id {}", campaignUsers.size(), pushCampaignId);
            for (CampaignUser campaignUser : campaignUsers) {
                LOG.info("users: {}", campaignUser.user().platformNumbers());
            }
            if(campaignUsers.isEmpty()) {
                throw new IllegalStateException("No campaign users found for campaign id " + pushCampaignId);
            }

            var rootNode = persistenceManager.getNodeGraph(nodeId);
            LOG.info("Found root node for campaign id {}: {}", pushCampaignId, rootNode);
            if(rootNode == null) {
                throw new IllegalStateException("Root node is null");
            }

            var scripts = new ScriptInterpreter(persistenceManager).translateNodeGraphToChttrScripts(nodeId);
            if (scripts == null || scripts.isEmpty()) {
                throw new IllegalStateException("No scripts derived for root node " + rootNode);
            } else {
                LOG.info("Generated {} ChttrScripts for campaign id {}", scripts.size(), pushCampaignId);
            }

            List<UserActor> actors = new ArrayList<>();
            for (var cu : campaignUsers) {
                var phoneNumber = cu.user().platformNumbers().get(Platform.SMS); // FIXME we can't hard code this dumbass
                if (phoneNumber != null) {
                    actors.add(new UserActor(phoneNumber, scripts));
                }
            }

            var client = new ChttrClient(properties, actors);
            client.start();

            // Gemini included this to keep the program running
            // even though the Helidon web server already handles it:
            //      Thread.currentThread().join();

        } catch (Exception e) {
            LOG.error("Error starting ChttrClient", e);
        }
    }
}
