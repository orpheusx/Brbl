package com.enoughisasgoodasafeast;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.PaddingLayout;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static com.enoughisasgoodasafeast.SharedConstants.CONNECTION_TIMEOUT_SECONDS;
import static io.helidon.http.Status.OK_200;

public class PlatformGatewayMT extends WebService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformGatewayMT.class);
    public static final int PORT = 2424;

    public void init() {
        LOG.info("Initializing PlatformGatewayMT");


        WebServer.builder()
                .port(2424)
                .connectionConfig(config -> {
                            config.connectTimeout(Duration.of(CONNECTION_TIMEOUT_SECONDS, ChronoUnit.SECONDS));
                            config.keepAlive(true);
                        }
                )
                .routing(router -> {
                            router.post("/mtReceive", new MTRecordingHandler());
                        }
                )
                .build()
                .start();
    }

    private static class MTRecordingHandler  implements Handler {

        @Override
        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            LOG.info("/mtReceive called");
            String rcvText = req.content().as(String.class);
            LOG.info("/mtReceive: posted content: {}", rcvText);
            res.status(OK_200);
            res.send("OK");
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

    public static void main(String[] args) {
        PlatformGatewayMT platformGatewayMT = new PlatformGatewayMT();
        platformGatewayMT.init();

    }
}
