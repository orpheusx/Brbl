package com.enoughisasgoodasafeast;

import ch.qos.logback.classic.Level;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.helidon.http.Status.Family.SUCCESSFUL;

public class HttpMessageSender {

    private static final Logger LOG = LoggerFactory.getLogger(HttpMessageSender.class);
    static {
        ((ch.qos.logback.classic.Logger) LOG).setLevel(Level.ERROR);
    }

    static final String PLATFORM_MT_PROTOCOL = "platform.mt.protocol";
    static final String PLATFORM_MT_HOST = "platform.mt.host";
    static final String PLATFORM_MT_PORT = "platform.mt.port";
    static final String PATH_INFO = "platform.mt.pathInfo";

    protected final String endpoint;
    protected final WebClient client;

    public HttpMessageSender(String endpoint) {
        this.endpoint = endpoint;
        LOG.info("Creating HttpMessageHandler with URL {}", endpoint);

        client = WebClient.builder()
                //.addService(WebClientTracing.create())
                .baseUri(endpoint)
                // TODO Telnyx requires the API KEY in a Bearer header, I think.
                // TODO Additional configuration that doesn't already define sensible defaults:
                //    read-timeout, connect-timeout

                // TODO Setup TLS support. Gotta have this but maybe not for initial implementation.
                // .tls(it -> it.trust(t -> t
                //     .keystore(k -> k.passphrase("password")
                //         .trustStore(true)
                //     .keystore(r -> r.resourcePath("client.p12")))))
                .build();
    }

    // TODO implement a head check here
    public boolean ping() {
        LOG.info("");
        return true;
    }

    // FIXME placeholder while we convert to using deliver(Message)
//    public boolean send(Message payload) {
//        return true;
//    }

    // FIXME The Helidon WebClient will throw an UncheckedIOException if it can't connect to the endpoint.
    //  Need to have a think about how we manage this possibility.
    public StatusException send(Message payload) {
        LOG.info("Sending message, '{}'", payload);

        try {
            String messageAsString = String.format("%s:%s:%s", payload.from(), payload.to(), payload.text());
            // FIXME need more robust error handling here...including retry logic.
            ClientResponseTyped<String> res = client.post().submit(messageAsString, String.class);
            LOG.info("Send response {}: {}", res.status(), res.entity());
            Status status = res.status();
            if (status.family() != SUCCESSFUL) {
                LOG.error("Post to {} failed: {}", endpoint, status);
                return new StatusException(status, null);
            } else {
                LOG.info("Post to {} OK: {}", endpoint, status);
                return new StatusException(status, null);
            }
        } catch (RuntimeException e) {
            LOG.error("Caught {} sending to {}", e.getMessage(), endpoint);
            return new StatusException(null, e);
        }
    }

}
