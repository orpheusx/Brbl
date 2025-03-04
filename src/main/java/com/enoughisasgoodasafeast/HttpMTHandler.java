package com.enoughisasgoodasafeast;

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static io.helidon.http.Status.Family.SUCCESSFUL;

/**
 * This is a very thin wrapper that, currently, doesn't handle any of the issues (throttling, transient outages, etc.)
 * with sending to a 3rd party messaging API (e.g. Slack, WhatsApp, etc.)
 * We should not consider it ready for anything but light, integration/unit testing.
 * A real implementation should use the application.yaml configuration support (for TLS setup, metrics, tracking)
 * provided by Helidon.
 * It might also want to leverage virtual threads to avoid blocking platform threads.
 * FIXME Seriously, fix this shit.
 */
public class HttpMTHandler implements MTHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpMTHandler.class);

    private final String endpoint;
    private final WebClient client;

    public HttpMTHandler(String endpoint) {
        this.endpoint = endpoint;
        LOG.info("Creating HttpMTHandler with URL {}", endpoint);

         client = WebClient.builder()
                //.addService(WebClientTracing.create())
                .baseUri(endpoint)
                .build();
    }

    public static MTHandler newHandler(Properties properties) {
        String protocol = properties.getProperty("platform.mt.protocol"); // TODO HTTP/2, also make constants
        String host = properties.getProperty("platform.mt.host");
        int port = Integer.parseInt(properties.getProperty("platform.mt.port"));
        String pathInfo = properties.getProperty("platform.mt.pathInfo"); //FIXME check for leading slash
        String endpoint = String.format("%s://%s:%d/%s", protocol, host, port, pathInfo);
        return new HttpMTHandler(endpoint);
    }

    public boolean handle(String payload) {
        return handle("/mtReceive", payload); // FIXME for fuck's sake use the fucking config, you fucking fuck
    }

    public boolean handle(String pathInfo, String payload) {
        LOG.info("Sending message, '{}'", payload);

        // FIXME need more robust error handling here...including retry logic.
        ClientResponseTyped<String> res = client.post().path(pathInfo).submit(payload, String.class);
        LOG.info("Send response {}: {}", res.status(), res.entity());
        Status status = res.status();
        if (status.family() != SUCCESSFUL) {
            LOG.error("Post to {} failed: {}", endpoint, status);
            return false;
        }
        else {
            return true;
        }
    }

    public static void main(String[] args) {
        new HttpMTHandler("http://localhost:4242");
    }
}
