package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.PersistenceManager;
import com.enoughisasgoodasafeast.operator.Platform;
import com.enoughisasgoodasafeast.operator.PostgresPersistenceManager;
import io.helidon.http.Header;
import io.helidon.http.HeaderValues;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.SharedConstants.*;
import static com.enoughisasgoodasafeast.SharedConstants.SERVER_NAME;
import static io.helidon.http.HeaderNames.SERVER;
import static io.helidon.http.HeaderValues.CONTENT_TYPE_TEXT_PLAIN;

public class ChttrService {

    private static final Logger LOG = LoggerFactory.getLogger(ChttrService.class);

    public void init(Properties properties) throws IOException, TimeoutException, PersistenceManager.PersistenceManagerException {
        LOG.info("Initializing CHTTR");

        PersistenceManager persistenceManager = PostgresPersistenceManager.createPersistenceManager(properties);
        int webServerPort = Integer.parseInt(properties.getProperty("chttr.listener.port"));
        LOG.info("Listening on port {}", webServerPort);

        WebServer.builder()
                .port(webServerPort)
                .connectionConfig(config -> {
                            config.connectTimeout(Duration.of(CONNECTION_TIMEOUT_SECONDS, ChronoUnit.SECONDS));
                            config.keepAlive(true);
                        }
                )
                .routing(router -> {
                            // TODO read up on the Helidon MP builtins for health checks.
                            router.post(CHTTR_SERVICE_ENDPOINT, new ChttrMessageHandler(persistenceManager));
                        }
                )
                .build()
                .start();
    }

    private static abstract class BaseHandler implements Handler {
        static final Header serverHeader = HeaderValues.createCached(SERVER, SERVER_NAME); //

        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            res.header(CONTENT_TYPE_TEXT_PLAIN);
            res.header(serverHeader);
        }
    }

    private static class ChttrMessageHandler extends BaseHandler {
        PersistenceManager persistenceManager;

        public ChttrMessageHandler(PersistenceManager persistenceManager) {
            this.persistenceManager = persistenceManager;
        }

        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            super.handle(req, res);
            LOG.info("{} requested", CHTTR_SERVICE_ENDPOINT); // make debug
            String rcvPayload = req.content().as(String.class); // write this to a log?
            Message mtMessage = marshall(rcvPayload);

            // Look up the UserActor that the message is being sent to.


        }

        public Message marshall(String payload) {
            String[] parsed = payload.split(":", 3);
            return new Message(MessageType.MT, Platform.BRBL, /*from*/ parsed[0], /*to*/ parsed[1], /*text*/ parsed[2]);
        }
    }
}
