package com.enoughisasgoodasafeast.sndr;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.MessageType;
import com.enoughisasgoodasafeast.operator.PersistenceManager;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.helidon.config.Config;
import com.enoughisasgoodasafeast.operator.PersistenceManager;
import io.helidon.http.Status;
import io.helidon.http.media.MediaSupport;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelnyxSender {

    private static final Logger LOG = LoggerFactory.getLogger(TelnyxSender.class);

    private final PersistenceManager persistenceManager;
    private final WebClient client;

    public TelnyxSender(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        //WebClient.builder()
        //        //.addService(WebClientTracing.create())
        //        .baseUri(endpoint)
        //        // TODO Telnyx requires the API KEY in a Bearer header, I think.
        //        // TODO Additional configuration that doesn't already define sensible defaults:
        //        //    read-timeout, connect-timeout
        //        // TODO Setup TLS support. Gotta have this but maybe not for initial implementation.
        //        // .tls(it -> it.trust(t -> t
        //        //     .keystore(k -> k.passphrase("password")
        //        //         .trustStore(true)
        //        //     .keystore(r -> r.resourcePath("client.p12")))))
        //        .build();
        // Create configuration (automatically loads application.yaml if YAML support is on the classpath)
        Config config = Config.create(); // loads "application.yaml", by default.

        // Telnyx API models use snake-case names so we have to map to Java conventions.
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        MediaSupport snakeCaseSupport = JacksonSupport.create(mapper);

        // Example payload:
        //        {
        //            "from":"+1977780497",
        //            "messaging_profile_id":"01a04ddd-624b-77ef-baac-e628266ff986",
        //            "to":"+17813301234",
        //            "text":"A test of the Telnyx gateway service.",
        //            "subject":null,
        //            "media_urls":null,
        //            "webhook_url":null,
        //            "webhook_failover_url":null,
        //            "use_profile_webhooks":true,
        //            "type":"SMS",
        //            "auto_detect":true,
        //            "send_at":null,
        //            "encoding":"auto"
        //        }

        client = WebClient.builder()
                .config(config.get("telnyx-sender"))
                .addMediaSupport(snakeCaseSupport)
                .build();

        LOG.info(config.get("telnyx-sender").get("base-uri").toString());
    }

    public void send(Message message) {
        // FIXME Change this direct call to a get() call on a Caffeine cache which calls the fetch as it's read-through method.
        var gwMeta = persistenceManager.fetchGatewayMeta(GatewayProvider.TELNYX, null, null, null); // FIXME need actual params here!
        ClientResponseTyped<String> res = client.post().submit(gwMeta.toGatewayMessage(message), String.class);
        LOG.info("Send response {}: {}", res.status(), res.entity());
    }

}
