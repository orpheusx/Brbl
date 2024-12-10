package com.enoughisasgoodasafeast;

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.helidon.http.Status.Family.SUCCESSFUL;

public class HttpMTHandler implements MTHandler {
    private static final Logger LOG = LoggerFactory.getLogger(HttpMTHandler.class);

    private final String uri;

    private final WebClient client;

    public HttpMTHandler(String uri) {
        this.uri = uri;
        LOG.info("Creating HttpMTHandler with URI: {}", uri);

         client = WebClient.builder()
                //.addService(WebClientTracing.create())
                .baseUri(uri)
                .build();
    }

    public boolean handle(String payload) {
        LOG.info("Sending message, '{}'", payload);

        // FIXME need more robust error handling here...including retry logic.
        ClientResponseTyped<String> res = client.post().path("/mtReceive").submit(payload, String.class);
        LOG.info("Send response {}: {}", res.status(), res.entity());
        Status status = res.status();
        if (status.family() != SUCCESSFUL) {
            LOG.error("Post to {} failed: {}", uri, status.code());
            return false;
        } else {
            return true;
        }

    }
}
