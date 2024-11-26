package com.enoughisasgoodasafeast;

import io.helidon.http.*;
import io.helidon.logging.common.HelidonMdc;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.UUID;
import java.util.logging.Logger;

import static com.enoughisasgoodasafeast.Constants.*;
import static io.helidon.http.HeaderNames.SERVER;
import static io.helidon.http.HeaderValues.CONTENT_TYPE_TEXT_PLAIN;
import static io.helidon.http.Status.OK_200;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class Rcvr {

    private static final Logger LOG = Logger.getLogger(Rcvr.class.getName());

    static {
        HelidonMdc.set(INSTANCE_ID, UUID.randomUUID().toString());
    }

    public void init() {
        LOG.info("Initializing RCVR");
        WebServer webServer = WebServer.builder()
                .port(4242)
                .connectionConfig(config -> {
                            config.connectTimeout(Duration.of(CONNECTION_TIMEOUT_SECONDS, ChronoUnit.SECONDS));
                            config.keepAlive(true);
                        }
                )
                .routing(router -> {
                            router.get("/foo", new TestResponseHandler());
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

    private static class HealthCheckHandler extends BaseHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            res.header(HeaderValues.create(HeaderNames.CONTENT_LENGTH, 0));
            super.handle(req, res);
            res.status(OK_200);
            res.send();
        }
    }

    private static class TestResponseHandler extends BaseHandler {
        static final byte[] content = "howdy\n".getBytes();
        static final int contentLen = content.length;

        @Override
        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            super.handle(req, res);
            res.header(HeaderValues.create(HeaderNames.CONTENT_LENGTH, contentLen));
            res.status(OK_200);
            res.send(content);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Rcvr rcvr = new Rcvr();
        Thread.sleep(1000);
        rcvr.init();
    }
}
