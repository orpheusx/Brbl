package com.enoughisasgoodasafeast;

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

    public void init() {
        LOG.info("Initializing RCVR");

        QueueProducer queueProducer;
        int webServerPort;
        try {
            final Properties properties = ConfigLoader.readConfig("rcvr.properties");
            queueProducer = RabbitQueueProducer.createQueueProducer(properties);
            webServerPort = Integer.parseInt(properties.getProperty("webserver.listener.port"));
            LOG.info("Listening on port {}", webServerPort);
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException(e);
        }

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
                            router.post(ENQUEUE_ENDPOINT, new EnqueueMessageHandler(queueProducer));
                            router.post(BRBL_ENQUEUE_ENDPOINT, new BrblMessageHandler(queueProducer));
                            // Some test only endpoints:
                            router.get("/foo", new HowdyTestResponseHandler());
                            router.post("/hello", new GoodbyeTestResponseHandler(queueProducer));
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
     * So we
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

    /**
     * FIXME Add metrics here
     */
    private static class EnqueueMessageHandler extends BaseHandler {

        QueueProducer queueProducer;

        public EnqueueMessageHandler(QueueProducer queueProducer) {
            LOG.info("Setup EnqueueMessageHandler");
            this.queueProducer = queueProducer;
        }

        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            super.handle(req, res);
//            LOG.info("/enqueue requested");
            String rcvText = req.content().as(String.class);
            // TODO produce an Message instead of just the String
            queueProducer.enqueue(rcvText); // TODO catch exceptions and persist the incoming message in a temp store?

            res.status(OK_200);
            res.send("OK");
            LOG.info("/enqueue: request content: {}", rcvText);
        }
    }

    private static class BrblMessageHandler extends BaseHandler {

        QueueProducer queueProducer;

        public BrblMessageHandler(QueueProducer queueProducer) {
            LOG.info("Setup BrblMessageHandler");
            this.queueProducer = queueProducer;
        }

        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            super.handle(req, res);
            LOG.info("{} requested", BRBL_ENQUEUE_ENDPOINT);
            String rcvPayload = req.content().as(String.class); // write this to a log?

            Message moMessage = marshall(rcvPayload);
            queueProducer.enqueue(moMessage);

            res.status(OK_200);
            res.send("OK"); // Is this bit needed?
            LOG.info("{} request content: {}", BRBL_ENQUEUE_ENDPOINT, moMessage);
        }

        public Message marshall(String payload) {
            String[] parsed = payload.split(":", 3);
            return new Message(MessageType.MO, Platform.BRBL, parsed[0], parsed[1], parsed[2]);
        }
    }


    private static class HowdyTestResponseHandler extends BaseHandler {
        static final String contentStr = "howdy\n";
        static final byte[] content = contentStr.getBytes();
        static final int contentLen = content.length;

        @Override
        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            super.handle(req, res);
            res.header(HeaderValues.create(HeaderNames.CONTENT_LENGTH, contentLen));
            res.status(OK_200);
            res.send(content);
            LOG.info(contentStr);
        }
    }

    // FIXME This should only be temporary. Remove ASAP.
    private static class GoodbyeTestResponseHandler extends BaseHandler {

        QueueProducer queueProducer;

        public GoodbyeTestResponseHandler(QueueProducer queueProducer) {
            LOG.info("Setup GoodbyeTestResponseHandler");
            this.queueProducer = queueProducer;
        }

        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            super.handle(req, res);
            LOG.info("/hello requested");
            String rcvText = req.content().as(String.class);

            // Expects a number followed by a space followed by "hello"
            String[] inputs = rcvText.split(" ", 2);
            if (inputs.length != 2) {
                LOG.error("Unexpected input to /hello: {}", rcvText);
                return;
            }

            LOG.info("Received {} --> {}", inputs[0], inputs[1]);

            String sndText = inputs[0] + " goodbye";
            queueProducer.enqueue(sndText);

//            Message newMO = new Message(...);

            res.status(OK_200);
            res.send();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Rcvr rcvr = new Rcvr();
        rcvr.init();
    }
}
