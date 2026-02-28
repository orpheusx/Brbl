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
import java.util.concurrent.ThreadLocalRandom;

import static com.enoughisasgoodasafeast.SharedConstants.CHTTR_SERVICE_ENDPOINT;
import static com.enoughisasgoodasafeast.SharedConstants.CONNECTION_TIMEOUT_SECONDS;
import static io.helidon.http.HeaderValues.CONTENT_TYPE_TEXT_PLAIN;

public class ChttrClient {

    private static final Logger LOG = LoggerFactory.getLogger(ChttrClient.class);
    private final Map<String, UserActor> userActors = new ConcurrentHashMap<>();
    private final MOHandler moHandler;
    private final Properties properties;

    public ChttrClient(Properties properties, List<UserActor> actors) {
        this.properties = properties;
        for (UserActor actor : actors) {
            userActors.put(actor.getPhoneNumber(), actor);
        }
        this.moHandler = HttpMOHandler.newHandler(properties);
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
                        }
                )
                .build()
                .start();
    }

    private class StatsInfoHandler implements Handler {
        @Override
        public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws Exception {

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

            processMessage(mtMessage);

            res.send("OK");
        }

        private @Nullable Message marshall(@NonNull String payload) {
            // Sndr uses HttpMessageHandler which sends "from:to:text".
            String[] parsed = payload.split(":", 3);
            if (parsed.length < 3) {
                 LOG.error("Invalid message format: {}", payload);
                 return null;
            }
            // In MT message, 'to' is the user's phone number.
            return new Message(MessageType.MT, Platform.SMS, parsed[0], parsed[1], parsed[2]);// FIXME Platform cannot be hardcoded!!!
        }

        @Override
        public void afterStop() {
            // Docker Compose appears to shut off logging output before this gets called.
            // Inspecting the container's logs directly, however, proves that is is called.
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
    }

    private void processMessage(Message mtIncomingMessage) {
        if (mtIncomingMessage == null) return;

        String phoneNumber = mtIncomingMessage.to(); // The user is the recipient of the MT message
        UserActor actor = userActors.get(phoneNumber);

        if (actor == null) {
            LOG.warn("No matching UserActor found for phone number: {}", phoneNumber);
            return;
        } else {
            actor.rcvdMessages.add(mtIncomingMessage);
        }

        Exchange matchingExchange = findMatchingExchange(actor, mtIncomingMessage.text());

        if (matchingExchange == null) {
            LOG.warn("No matching Exchange found for message text: '{}' for user: {}", mtIncomingMessage.text(), phoneNumber);
            return;
        }

        // TODO Make looping behavior configurable. For now, we check for cycles and stop if we see one.
        if(actor.hasVisited(matchingExchange)) {
            LOG.info("Cycle detected: stopping execution for UserActor: {}", phoneNumber);
            LOG.info("Node causing cycle: {}", matchingExchange.mtText());
            return;
        } else {
            actor.visit(matchingExchange);
        }

        if (matchingExchange.moResponses() != null && !matchingExchange.moResponses().isEmpty()) {
            String responseText = matchingExchange.moResponses().get(ThreadLocalRandom.current().nextInt(matchingExchange.moResponses().size()));
            
            // Create MO message. 'from' is the user (phoneNumber), 'to' is the sender of the MT message.
            Message moResponseMessage = new Message(MessageType.MO, mtIncomingMessage.platform(), phoneNumber, mtIncomingMessage.from(), responseText);
            
            boolean success = moHandler.handle(moResponseMessage);
            if (success) {
                LOG.info("Response sent from {} with {}", phoneNumber, moResponseMessage);
                actor.sentMessages.add(moResponseMessage);
            } else {
                LOG.error("Response fail from {} with {}", phoneNumber, moResponseMessage);
            }
        } else {
            LOG.info("No MO responses defined for exchange: {}", matchingExchange.mtText());
        }
    }

    private @Nullable Exchange findMatchingExchange(@NonNull UserActor actor, @NonNull String text) {
        // Gemini wrote this functional way of finding a match. Looks cool though a regular loop is more efficient for smaller list (<1k) like this one. :-[

        return actor.getScript().exchanges.stream()
                .filter(e -> e.mtText().equals(text))
                .findFirst()
                .orElse(null);
    }

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
            Properties properties = ConfigLoader.readConfig("chttr.properties"); // Assuming a config file

            PersistenceManager persistenceManager = PostgresPersistenceManager.createPersistenceManager(properties);

            Collection<CampaignUser> campaignUsers = persistenceManager.getPushCampaignUsers(pushCampaignId, DeliveryStatus.PENDING);
            LOG.info("Found {} campaign users for campaign id {}", campaignUsers.size(), pushCampaignId);
            
            ScriptInterpreter scriptInterpreter = new ScriptInterpreter(persistenceManager);
            ChttrScript script = scriptInterpreter.translateNodeGraphToChttrScript(nodeId);

            List<UserActor> actors = new ArrayList<>();
            for (CampaignUser cu : campaignUsers) {
                String phoneNumber = cu.user().platformNumbers().get(Platform.SMS); // FIXME we can't hard code this dumbass
                if (phoneNumber != null) {
                    actors.add(new UserActor(phoneNumber, script));
                }
            }

            ChttrClient client = new ChttrClient(properties, actors);
            client.start();

            // Gemini included this to keep the program running
            // even though the Helidon web server already handles it:
            //      Thread.currentThread().join();

        } catch (Exception e) {
            LOG.error("Error starting ChttrClient", e);
        }
    }
}
