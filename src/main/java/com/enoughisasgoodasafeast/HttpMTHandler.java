package com.enoughisasgoodasafeast;

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static io.helidon.http.Status.Family.SUCCESSFUL;

public class HttpMTHandler implements MTHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpMTHandler.class);

    private final String endpoint;
    private final WebClient client;

    public HttpMTHandler(String endpoint) {
        this.endpoint = endpoint;
        LOG.info("Creating HttpMTHandler with URI: {}", endpoint);

         client = WebClient.builder()
                //.addService(WebClientTracing.create())
                .baseUri(endpoint)
                .build();
    }

    public boolean handle(String payload) {
        return handle("/mtReceive", payload); // FIXME for fuck's sake use the fucking config, you fucking fuck
    }

    public static MTHandler newHandler(Properties properties) {
        String protocol = properties.getProperty("platform.mt.protocol"); // TODO TLS and HTTP/2
        String host = properties.getProperty("platform.mt.host");
        int port = Integer.parseInt(properties.getProperty("platform.mt.port"));
        String pathInfo = properties.getProperty("platform.mt.pathInfo"); //FIXME check for leading '/'
        String endpoint = String.format("%s://%s:%d", protocol, host, port);
        return new HttpMTHandler(endpoint);
    }

    public boolean handle(String pathInfo, String payload) {
        LOG.info("Sending message, '{}'", payload);

        // FIXME need more robust error handling here...including retry logic.
        ClientResponseTyped<String> res = client.post().path(pathInfo).submit(payload, String.class);
        LOG.info("Send response {}: {}", res.status(), res.entity());
        Status status = res.status();
        if (status.family() != SUCCESSFUL) {
            LOG.error("Post to {} failed: {}", endpoint, status.code());
            return false;
        } else {
            return true;
        }

    }

    public static void main(String[] args) {
        new HttpMTHandler("http://localhost:4242");
    }
}
