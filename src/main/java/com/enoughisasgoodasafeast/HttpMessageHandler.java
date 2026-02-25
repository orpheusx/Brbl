package com.enoughisasgoodasafeast;

import ch.qos.logback.classic.Level;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.helidon.http.Status.Family.SUCCESSFUL;

public class HttpMessageHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpMessageHandler.class);
    static {
        ((ch.qos.logback.classic.Logger) LOG).setLevel(Level.ERROR);
    }

    protected final String endpoint;
    protected final WebClient client;

    public HttpMessageHandler(String endpoint) {
        this.endpoint = endpoint;
        LOG.info("Creating HttpMessageHandler with URL {}", endpoint);

        client = WebClient.builder()
                //.addService(WebClientTracing.create())
                .baseUri(endpoint)
                .build();
    }

    // TODO implement a head check here
    public boolean ping() {
        LOG.info("");
        return true;
    }

    // FIXME The Helidon WebClient will throw an UncheckedIOException if it can't connect to the endpoint.
    //  Need to have a think about how we manage this possibility.
    public boolean handle(Message payload) {
        LOG.info("Sending message, '{}'", payload);

        String messageAsString = String.format("%s:%s:%s", payload.from(), payload.to(), payload.text());
        // FIXME need more robust error handling here...including retry logic.
        ClientResponseTyped<String> res = client.post().submit(messageAsString, String.class);
        LOG.info("Send response {}: {}", res.status(), res.entity());
        Status status = res.status();
        if (status.family() != SUCCESSFUL) {
            LOG.error("Post to {} failed: {}", endpoint, status);
            return false;
        } else {
            LOG.info("Post to {} OK: {}", endpoint, status);
            return true;
        }
    }

}
