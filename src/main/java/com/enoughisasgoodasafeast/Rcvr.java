package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.PersistenceManager;
import com.enoughisasgoodasafeast.operator.PostgresPersistenceManager;
import com.enoughisasgoodasafeast.operator.Platform;
import io.helidon.http.*;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import org.slf4j.Logger;

import static com.enoughisasgoodasafeast.SharedConstants.*;
import static io.helidon.http.HeaderNames.SERVER;
import static io.helidon.http.HeaderValues.CONTENT_TYPE_TEXT_PLAIN;
import static io.helidon.http.Status.OK_200;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class Rcvr extends WebService {

    private static final Logger LOG = LoggerFactory.getLogger(Rcvr.class);

    public void init(Properties properties) throws IOException, TimeoutException, PersistenceManager.PersistenceManagerException {
        LOG.info("Initializing RCVR");

        QueueProducer queueProducer = RabbitQueueProducer.createQueueProducer(properties);
        PersistenceManager persistenceManager = PostgresPersistenceManager.createPersistenceManager(properties);
        int webServerPort = Integer.parseInt(properties.getProperty("webserver.listener.port"));
        LOG.info("Listening on port {}", webServerPort);

        WebServer.builder()
                .port(webServerPort)
                .connectionConfig(config -> {
                            config.connectTimeout(Duration.of(CONNECTION_TIMEOUT_SECONDS, ChronoUnit.SECONDS));
                            config.keepAlive(true);
                        }
                )
                .routing(router -> {
                            // Supported endpoints:
                            router.get(HEALTH_ENDPOINT, new HealthCheckHandler());
                            router.post(BRBL_ENQUEUE_ENDPOINT, new BrblMessageHandler(queueProducer, persistenceManager));
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

    /**
     * Only address the functionality of the Rcvr itself, not the target queuing system.
     * FIXME Helidon MP includes /health/live, /health/ready, and /health/started endpoints. We should probably just use those.
     */
    private static class HealthCheckHandler extends BaseHandler {

        final static Header OK_CONTENT_LEN_HEADER = HeaderValues.create(HeaderNames.CONTENT_LENGTH, 0);

        @Override
        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            res.header(OK_CONTENT_LEN_HEADER);
            super.handle(req, res);
            res.status(OK_200);
            res.send();
        }
    }

    private static class BrblMessageHandler extends BaseHandler {

        QueueProducer queueProducer;
        PersistenceManager persistenceManager;

        public BrblMessageHandler(QueueProducer queueProducer, PersistenceManager persistenceManager) {
            LOG.info("Setup BrblMessageHandler");
            this.queueProducer = queueProducer;
            this.persistenceManager = persistenceManager;
        }

        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            super.handle(req, res);
            LOG.info("{} requested", BRBL_ENQUEUE_ENDPOINT); // make debug
            String rcvPayload = req.content().as(String.class); // write this to a log?

            Message moMessage = null;
            try {
                moMessage = marshall(rcvPayload);
                LOG.info("{} request content: {}", BRBL_ENQUEUE_ENDPOINT, moMessage); // make debug

                queueProducer.enqueue(moMessage);

                boolean isInsertOk = persistenceManager.insertMO(moMessage);
                if (!isInsertOk) {
                    // TODO increment a database specific error counter metric in Prometheus?
                    LOG.error("Failed to log enqueued message, {}", moMessage);
                }

            } catch (IOException e) {
                LOG.error("Error handling message: {}", rcvPayload);
                LOG.error("Cause:", e);
            }
            /*finally {
                // TODO increment a queue specific error counter metric in Prometheus?
            }*/

            res.status(OK_200);
            res.send("OK"); // Is this bit needed?
        }

        public Message marshall(String payload) {
            String[] parsed = payload.split(":", 3);
            return new Message(MessageType.MO, Platform.BRBL, /*from*/parsed[0], /*to*/parsed[1], /*text*/parsed[2]);
        }
    }

    public static void main(String[] args) throws Exception {
        Rcvr rcvr = new Rcvr();
        rcvr.init(ConfigLoader.readConfig("rcvr.properties"));
    }

}
