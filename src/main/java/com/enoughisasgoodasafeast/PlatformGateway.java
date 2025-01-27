package com.enoughisasgoodasafeast;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.enoughisasgoodasafeast.SharedConstants.CONNECTION_TIMEOUT_SECONDS;
import static com.enoughisasgoodasafeast.SharedConstants.ENQUEUE_ENDPOINT;
import static io.helidon.http.Status.OK_200;
import static io.helidon.http.Status.TOO_MANY_REQUESTS_429;
import static java.time.temporal.ChronoUnit.*;

/**
 * This is a simulated and generic representation of a 3rd party message gateway intended for use with unit/integration
 * testing.
 */
public class PlatformGateway extends WebService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformGateway.class);
    // private static final String MO_PATH = "/moReceive";
    private static final String MT_PATH = "/mtReceive";
    private final GatewaySimStrategy strategy;

    public int port = 2424; // the port we listen on
    public MessageDirection direction = MessageDirection.MT;
    public String destinationUrl;
    private HttpMTHandler client;
    public RecordingHandler recordingHandler;
    private WebServer webServer;

    public enum MessageDirection {
        MO, // Mobile Originated
        MT  // Mobile Terminated
    }

    // public PlatformGateway() throws IOException {
    //     Properties props = ConfigLoader.readConfig("gateway.properties");
    //     rcvrHost = InetAddress.getLocalHost().getHostAddress(); // rcvrUri = props.getProperty("rcvr.host");
    //     rcvrPort = Integer.parseInt(props.getProperty("rcvr.port"));
    //     direction = MessageDirection.MT; // Add to config?
    //     String destinationUrl = String.format("http://%s:%d", rcvrHost, rcvrPort);
    //     client = /*(HttpMTHandler) HttpMTHandler.newHandler(props); */ new HttpMTHandler(destinationUrl);
    //     recordingHandler = new RecordingHandler();
    // }

    public PlatformGateway(String destinationUrl) {
        this.destinationUrl = destinationUrl;
        strategy = null;
    }

    public PlatformGateway(String destinationUrl, GatewaySimStrategy strategy) {
        this.destinationUrl = destinationUrl;
        this.strategy = strategy;
    }

    public void init() {
        LOG.info("Initializing PlatformGateway[{}]", direction);

        recordingHandler = new RecordingHandler(this.strategy);

        webServer = buildNewWebServer(recordingHandler)
                .start();

        client = new HttpMTHandler(destinationUrl);
    }

    private WebServer buildNewWebServer(RecordingHandler recordingHandler) {
        return WebServer.builder()
                .port(this.port)
                .connectionConfig(config -> {
                            config.connectTimeout(Duration.of(CONNECTION_TIMEOUT_SECONDS, SECONDS));
                            config.keepAlive(true);
                        }
                )
                .routing(router -> {
                    router.post(MT_PATH, recordingHandler);
                })
                .build();
    }

    public void stop() {
        webServer.stop();
        LOG.info("Stopped PlatformGateway web server.");
    }

    public void restart() {
        // recordingHandler.reset();
        // Helidon doesn't support restarting the server so make a new instance
        webServer = buildNewWebServer(recordingHandler).start();
        LOG.info("Restarted PlatformGateway web server");
    }

    public void resetHistory() {
        recordingHandler.reset();
        LOG.info("Reset PlatformGateway history.");
    }

    public void acceptAllTraffic() {
        recordingHandler.isAcceptingAllTraffic = true;
        LOG.info("PlatformGateway **not** accepting traffic.");
    }

    public void rejectAllTraffic() {
        recordingHandler.isAcceptingAllTraffic = false;
        LOG.info("PlatformGateway accepting traffic.");
    }

    public void filterTrafficByStrategy(boolean filter) {
        recordingHandler.useStrategy(filter);
    }

    public static class RecordingHandler implements Handler {

        private final ArrayList<String> recorder = new ArrayList<>();
        private final GatewaySimStrategy strategy;
        boolean isAcceptingAllTraffic = true;
        private boolean filterByStrategy;

        public RecordingHandler() {
            strategy = null;
        }

        public RecordingHandler(GatewaySimStrategy strategy) {
            this.strategy = strategy;
        }

        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            String rcvText = req.content().as(String.class);
            if (filterByStrategy && strategy!=null && !strategy.canAccept(rcvText)) {
                LOG.info("GatewaySimStrategy rejecting message: {}", rcvText);

                res.status(TOO_MANY_REQUESTS_429);
                res.send();
                return;
            }
            if (isAcceptingAllTraffic) { // Which should take precedence? acceptAllTraffic or filterByStrategy?

                LOG.info("Received message: {}", rcvText);

                record(rcvText);

                res.status(OK_200);
                res.send("OK");
            } else {
                // simulate throttling
                LOG.info("Rejecting post with 429 Too Many Requests: {}", rcvText);
                // record(rcvText); // record the rejection?
                res.status(TOO_MANY_REQUESTS_429); // or, for fun, I_AM_A_TEAPOT_418
                res.send();
            }
        }

        public void record(String message) {
            recorder.add(message);
        }

        public List<String> retrieve() {
            final ArrayList<String> copiedList = new ArrayList<>(recorder.size());
            copiedList.addAll(recorder);
            return copiedList;
        }

        public void reset() {
            recorder.clear();
        }

        @Override
        public void beforeStart() {
            Handler.super.beforeStart();
        }

        @Override
        public void afterStop() {
            Handler.super.afterStop();
        }

        public void useStrategy(boolean filter) {
            this.filterByStrategy = filter;
        }
    }

    public void sendMoTraffic(String message) {
        this.client.handle(ENQUEUE_ENDPOINT, message); // FIXME consider using a shared constant for the pathInfo here and in Rcvr
    }

    // public static void main(String[] args) throws IOException {
    //     PlatformGateway platformGateway = new PlatformGateway(/*"http://192.168.1.155:14242"*/);
    //     platformGateway.init();
    // }
}
