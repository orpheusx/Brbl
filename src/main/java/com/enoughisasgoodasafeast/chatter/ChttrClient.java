package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.operator.*;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
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

    private class ChttrMessageHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            LOG.info("Handling incoming message...");
            res.header(CONTENT_TYPE_TEXT_PLAIN);
            String rcvPayload = req.content().as(String.class);
            Message mtMessage = marshall(rcvPayload);

            processMessage(mtMessage);

            res.send("OK");
        }

        private Message marshall(String payload) {
            // Sndr uses HttpMessageHandler which sends "from:to:text".
            String[] parsed = payload.split(":", 3);
            if (parsed.length < 3) {
                 LOG.error("Invalid message format: {}", payload);
                 return null;
            }
            // In MT message, 'to' is the user's phone number.
            return new Message(MessageType.MT, Platform.WAP, parsed[0], parsed[1], parsed[2]);
        }
    }

    private void processMessage(Message message) {
        if (message == null) return;

        String phoneNumber = message.to(); // The user is the recipient of the MT message
        UserActor actor = userActors.get(phoneNumber);

        if (actor == null) {
            LOG.warn("No matching UserActor found for phoneNumber: {}", phoneNumber);
            return;
        }

        Exchange matchingExchange = findMatchingExchange(actor, message.text());

        if (matchingExchange == null) {
            LOG.warn("No matching Exchange found for message text: '{}' for user: {}", message.text(), phoneNumber);
            return;
        }

        if (matchingExchange.moResponses != null && !matchingExchange.moResponses.isEmpty()) {
            String responseText = matchingExchange.moResponses.get(ThreadLocalRandom.current().nextInt(matchingExchange.moResponses.size()));
            
            // Create MO message. 'from' is the user (phoneNumber), 'to' is the sender of the MT message.
            Message moMessage = new Message(MessageType.MO, Platform.WAP, phoneNumber, message.from(), responseText);
            
            boolean success = moHandler.handle(moMessage);
            if (success) {
                LOG.info("Responded to {} with: {}", phoneNumber, moMessage);
            } else {
                LOG.error("Failed to send response for {}", phoneNumber);
            }
        } else {
            LOG.info("No MO responses defined for exchange: {}", matchingExchange.mtText);
        }
    }

    private Exchange findMatchingExchange(UserActor actor, String text) {
        // The prompt says: "The text of the Message must then be used to find the Exchange element in the UserActor's script (the ChttrScript) whose mtText matches."
        // We need access to the script. UserActor has it but it's private.
        // I'll need to modify UserActor to expose the script or add a method to find the exchange.
        // For now, let's assume I can modify UserActor or use reflection if I really had to, but modifying is better.
        // I will modify UserActor to expose the script.
        return actor.getScript().exchanges.stream()
                .filter(e -> e.mtText.equals(text))
                .findFirst()
                .orElse(null);
    }

    public static void main(String[] args) {
//        if (args.length != 2) {
//            System.err.println("Usage: ChttrClient <pushCampaignId> <nodeId>");
//            for (String arg : args) {
//                System.err.println("arg: " + arg);
//            }
//            return;
//        }

        UUID pushCampaignId;
        UUID nodeId;
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
            
            ScriptInterpreter scriptInterpreter = new ScriptInterpreter(persistenceManager);
            ChttrScript script = scriptInterpreter.translateNodeGraphToChttrScript(nodeId);

            List<UserActor> actors = new ArrayList<>();
            for (CampaignUser cu : campaignUsers) {
                String phoneNumber = cu.user().platformNumbers().get(Platform.WAP);
                if (phoneNumber != null) {
                    actors.add(new UserActor(phoneNumber, script));
                }
            }

            ChttrClient client = new ChttrClient(properties, actors);
            client.start();

            // Gemini included this to keep the program running
            // even though web server already handles this.
            // Thread.currentThread().join();

        } catch (Exception e) {
            LOG.error("Error starting ChttrClient", e);
        }
    }
}
