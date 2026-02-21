package com.enoughisasgoodasafeast;

import ch.qos.logback.classic.Level;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static io.helidon.http.Status.Family.SUCCESSFUL;

/**
 * A thin wrapper around WebClient that, currently, doesn't handle any of the issues (throttling, transient outages, etc.)
 * with sending to a 3rd party messaging API (e.g. Slack, WhatsApp, etc.) or, for that matter, our own Rcvr.
 * We should not consider it ready for anything but light, integration/unit testing.
 * A real implementation should use the application.yaml configuration support (for TLS setup, metrics, tracking)
 * provided by Helidon.
 */
public class HttpMTHandler extends HttpMessageHandler implements MTHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpMTHandler.class);
    static {
        ((ch.qos.logback.classic.Logger) LOG).setLevel(Level.ERROR);
    }

    public HttpMTHandler(String endpoint) {
        super(endpoint);
    }

    public static MTHandler newHandler(Properties properties) {
        String protocol = properties.getProperty("platform.mt.protocol"); // TODO HTTP/2, also make constants
        String host = properties.getProperty("platform.mt.host");
        int port = Integer.parseInt(properties.getProperty("platform.mt.port"));
        String pathInfo = properties.getProperty("platform.mt.pathInfo");
        // Check for leading slash in the provided pathInfo
        if (pathInfo.endsWith("/")) {
            return new HttpMTHandler(String.format("%s://%s:%d%s", protocol, host, port, pathInfo));
        } else {
            return new HttpMTHandler(String.format("%s://%s:%d/%s", protocol, host, port, pathInfo));
        }
    }

    public static void main(String[] args) {
        final HttpMTHandler handler = new HttpMTHandler("http://localhost:2424/mtReceive");
        final boolean ok = handler.handle(Message.newMO("1234567890", "01234", "fromhost"));
        LOG.info("send ok?: {}", ok);
    }
}
