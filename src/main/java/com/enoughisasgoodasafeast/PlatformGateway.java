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
import java.util.Properties;

import static com.enoughisasgoodasafeast.SharedConstants.CONNECTION_TIMEOUT_SECONDS;
import static com.enoughisasgoodasafeast.SharedConstants.ENQUEUE_ENDPOINT;
import static io.helidon.http.Status.OK_200;
import static java.time.temporal.ChronoUnit.*;

/**
 * This is a simulated and generic representation of a 3rd party message gateway intended for use with unit/integration
 * testing.
 */
public class PlatformGateway extends WebService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformGateway.class);
    // private static final String MO_PATH = "/moReceive";
    private static final String MT_PATH = "/mtReceive";

    public int port = 2424; // the port we listen on
    public MessageDirection direction = MessageDirection.MT;
//    public Properties props;
    public String rcvrUri;
    public int rcvrPort;
    private HttpMTHandler client;
    public RecordingHandler recordingHandler;

    public enum MessageDirection {
        MO, // Mobile Originated
        MT  // Mobile Terminated
    }

    /* Used by EndToEndMessagingTest*/
    public PlatformGateway() throws IOException {
        Properties props = ConfigLoader.readConfig("gateway.properties");
        rcvrUri = props.getProperty("rcvr.host");
        rcvrPort = Integer.parseInt(props.getProperty("rcvr.port"));
        direction = MessageDirection.MT; // Add to config?
        String destinationUrl = String.format("http://%s:%d", rcvrUri, rcvrPort);
        client = (HttpMTHandler) HttpMTHandler.newHandler(props); //new HttpMTHandler(destinationUrl);
        recordingHandler = new RecordingHandler();
    }

     public PlatformGateway(String destinationUrl) throws IOException {
        direction = MessageDirection.MT;
        client = new HttpMTHandler(destinationUrl);
        recordingHandler = new RecordingHandler();
     }

    public void init() {
        LOG.info("Initializing PlatformGateway[{}]", direction);

        WebServer.builder()
                .port(this.port)
                .connectionConfig(config -> {
                            config.connectTimeout(Duration.of(CONNECTION_TIMEOUT_SECONDS, SECONDS));
                            config.keepAlive(true);
                        }
                )
                .routing(router -> {
                    router.post(MT_PATH, recordingHandler);
                })
                .build()
                .start();
    }

    public static class RecordingHandler implements Handler {

        private final ArrayList<String> recorder = new ArrayList<>();

        @Override
        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            String rcvText = req.content().as(String.class);
            LOG.info("Received content: {}", rcvText);

            record(rcvText);

            res.status(OK_200);
            res.send("OK");
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
    }

    public void sendMoTraffic(String message) {
        this.client.handle(ENQUEUE_ENDPOINT, message); // FIXME consider using a shared constant for the pathInfo here and in Rcvr
    }

     public static void main(String[] args) throws IOException {
         PlatformGateway platformGateway = new PlatformGateway(/*"http://192.168.1.155:14242"*/);
         platformGateway.init();
     }
}
