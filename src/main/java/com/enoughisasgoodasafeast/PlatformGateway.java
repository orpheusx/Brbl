package com.enoughisasgoodasafeast;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static ch.qos.logback.classic.Level.*;
import static com.enoughisasgoodasafeast.SharedConstants.*;
import static io.helidon.http.Status.OK_200;
import static io.helidon.http.Status.TOO_MANY_REQUESTS_429;
import static java.time.temporal.ChronoUnit.*;

/**
 * This is a simulated and generic representation of a 3rd party message gateway intended for use with unit/integration
 * testing.
 */
public class PlatformGateway extends WebService {

    public static final Logger LOG = LoggerFactory.getLogger(PlatformGateway.class);
    static {
        // mute the loggers for use with CLI
        ((ch.qos.logback.classic.Logger) LOG).setLevel(ERROR);
        ((ch.qos.logback.classic.Logger)LoggerFactory.getLogger("io.helidon.common.features.HelidonFeatures")).setLevel(ERROR);
        ((ch.qos.logback.classic.Logger)LoggerFactory.getLogger("io.helidon.webserver.ServerListener")).setLevel(ERROR);
        ((ch.qos.logback.classic.Logger)LoggerFactory.getLogger("io.helidon.logging.slf4j.Slf4jProvider")).setLevel(ERROR);
        ((ch.qos.logback.classic.Logger)LoggerFactory.getLogger("io.helidon.webserver.LoomServer")).setLevel(ERROR);
        ((ch.qos.logback.classic.Logger)LoggerFactory.getLogger("io.helidon.Main")).setLevel(ERROR);
    }

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
        private boolean isFilterByStrategy;
        private RecordingHandlerListener listener;

        public RecordingHandler() {
            strategy = null;
        }

        public RecordingHandler(GatewaySimStrategy strategy) {
            this.strategy = strategy;
        }

        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            String rcvText = req.content().as(String.class);
            if (isFilterByStrategy && strategy!=null && !strategy.canAccept(rcvText)) {
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
            if (listener != null) {
                listener.notify(message);
            }
        }

        public void addListener(RecordingHandlerListener listener) {
            this.listener = listener;
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
            this.isFilterByStrategy = filter;
        }
    }

    public void sendMoTraffic(Message message) {
        this.client.handle(message);
    }

    public static void sendFiveAndQuit() {
        // move some of the stuff from main to here
        // then add some switches on args to support
        // different modes.
    }

     public static void main(String[] args) throws IOException, InterruptedException {
         PlatformGateway platformGateway = new PlatformGateway("http://192.168.1.155:4242" + BRBL_ENQUEUE_ENDPOINT);
         platformGateway.init();

         Message[] moTraffic = {
                 Message.newMO("17817299468","1234","1 hello"),
                 Message.newMO("17817299469","1234","2 hi"),
                 Message.newMO("17817299470","1234","3 hea"),
                 Message.newMO("17817299471","1234","4 hey here"),
                 Message.newMO("17817299472","1234","5 greetings")
         };

         for (Message mo : moTraffic) {
             platformGateway.sendMoTraffic(mo);
         }

         LOG.info("All MOs sent.");

         Thread.sleep(3000);

         LOG.info("Response MTs received: {}", platformGateway.recordingHandler.retrieve().size());
     }
}
