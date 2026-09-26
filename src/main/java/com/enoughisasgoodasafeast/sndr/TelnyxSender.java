package com.enoughisasgoodasafeast.sndr;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.RetryDelayRoutingKey;
import com.enoughisasgoodasafeast.datagen.KnownData;
import com.enoughisasgoodasafeast.operator.PersistenceManager;
import com.enoughisasgoodasafeast.operator.ProcessState;
import com.enoughisasgoodasafeast.sndr.server.model.CreateMessageRequest;
import com.enoughisasgoodasafeast.sndr.server.model.MessageResponse;
import com.enoughisasgoodasafeast.sndr.server.model.MessagingErrors;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.VerboseBlockingBucket;
import io.helidon.config.Config;
import io.helidon.http.*;
import io.helidon.http.media.MediaSupport;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Optional;

public class TelnyxSender {

    private static final Logger LOG = LoggerFactory.getLogger(TelnyxSender.class);

    private static final HeaderName RETRY_AFTER = HeaderNames.create("retry-after");
    private static final HeaderName RATE_LIMIT_RESET = HeaderNames.create("x-ratelimit-reset");

    private final PersistenceManager persistenceManager;
    private final WebClient client;
    private final VerboseBlockingBucket rateLimiter;

    public TelnyxSender(PersistenceManager persistenceManager) {

        this.persistenceManager = persistenceManager;

        // Create configuration (automatically loads application.yaml if YAML support is on the classpath)
        Config config = Config.create();

        // Telnyx API models use snake-case names so we have to map to Java conventions.
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.registerModule(new JavaTimeModule());
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL); // I think this matches Telnyx expectations.
        MediaSupport snakeCaseSupport = JacksonSupport.create(mapper);

        // Example payload (in Java object format, raw JSON properties are all snake-cased):
        //  {
        //            "from":"+1977780497",
        //            "messaging_profile_id":"01a04ddd-624b-77ef-baac-e628266ff986",
        //            "to":"+17813301234",
        //            "text":"A test of the Telnyx gateway service.",
        //            "subject":null,
        //            "media_urls":[],
        //            "webhook_url":null,
        //            "webhook_failover_url":null,
        //            "use_profile_webhooks":true,
        //            "type":"SMS",
        //            "auto_detect":true,
        //            "send_at":null,
        //            "encoding":"auto"
        //  }

        client = WebClient.builder()
                .config(config.get("telnyx-sender"))
                // TODO Can we set the Bearer header with our API key here or does it happen each time we make a post?
                .addMediaSupport(snakeCaseSupport)
                // FIXME implement a secure means of retrieving the appropriate auth token...
                .addHeader(HeaderValues.create(HeaderNames.AUTHORIZATION, "Bearer " + "foobar"))
                    //.addService(WebClientTracing.create())
                    // TODO See application.yaml to enable TLS setup.
                .build();

        LOG.info(config.get("telnyx-sender").get("base-uri").toString());

        // TODO
        // Maybe try a HEAD request to confirm DNS is working before we start reading messages from the queue?

        // TODO We need a separate limiter for each customer's
        rateLimiter = Bucket.builder()
                .addLimit(limit ->
                        limit.capacity(35)
                        // Either of these methods seem to work, at least for our integration tests.
                        .refillIntervally(35, Duration.ofSeconds(1)))
                        //  .refillGreedy(35, Duration.ofSeconds(1)))
                .build().asBlocking().asVerbose();

    }

        /*
         * Pseudocode for guaranteeing strict ordering of MTs to the same number/SessionKey:
         *   SessionKey sk = SessionKey.newSessionKey(message)
         *   // gotta synchronize some of this. Should the list of messages be a linkedlist?
         *       var sessionScopeMessages = inflightCache.get(sk) // List<Message> plus some info about the time the message was added to the list
         *       if(sessionScopeMessages==null)
         *           sessionScopeMessages = new List<Message>()
         *           inflightCache.put(sk, sessionScopeMessages)
         *       sessionScopeMessages.add(message)
         *
         */

    /**
     * Attempt to hand off the given Message to the Telnyx service.
     * @param message the standard Brbl formatted message
     * @return the tuple combining the result of the send and the routing key to be used if it needs to be retried
     *  (or null if no retry is needed.)
     */
    public ProcessStateRoutingKey send(Message message, RouteInfo routeInfo) {
        LOG.info("Sending message: {}", message);
        // We're assuming that we have one instance of a Sender for each third-party we work with.
        // I think we need to track company-scoped (assuming each has their own messaging_profile_id) and SessionKey-scoped throttle state where
        // delays and retries are concerned.
        // The SessionKey scope is needed to avoid out-of-order delivery due to transient retries e.g. two messages are enqueued, the first gets a
        // retry delay and put on a delay queue, the second comes along just after the delay expires and is sent immediately, ahead of the first
        // message. A WeakHashMap doesn't quite fit the use case; a time-expired Caffeine cache might...
        // Should we think about sending serially through a barrier? It would certainly make it easier to think about...

        try {
            rateLimiter.consume(1);
        } catch (InterruptedException e) {
            // Not sure what might be triggering this so not sure the best way to handle it.
            LOG.warn("Interrupted in rate-limiter: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        try (final HttpClientResponse res = client.post()
                .header(HeaderNames.AUTHORIZATION, authToken(message, routeInfo))
                .submit(toGatewayMessage(message, routeInfo))) {
            final var status = res.status();
            final var headers = res.headers();

            return switch (status.code()) {

                case 200 -> {
                    LOG.debug("200: {} -> {}", message.id(), res.as(MessageResponse.class).getData().getId());
                    yield new ProcessStateRoutingKey(ProcessState.OK);
                }
                case 429 -> {
                    LOG.debug("429: {}", res.as(MessagingErrors.class)); // temporary failure
                    yield new ProcessStateRoutingKey(ProcessState.RETRY, routingKeyForDelay(headers));
                }
                case 422 -> {
                    LOG.debug("422: {}", res.as(MessagingErrors.class)); // e.g. conflicting message properties
                    yield new ProcessStateRoutingKey(ProcessState.ERROR);
                }
                case 400 -> {
                    LOG.debug("400: {}", res.as(MessagingErrors.class)); // e.g. wrong/missing message properties
                    yield new ProcessStateRoutingKey(ProcessState.ERROR);
                }
                default -> { // e.g. 404, 503, etc.
                    LOG.error("Unhandled Response: {}: {}", status, res.as(String.class));
                    yield new ProcessStateRoutingKey(ProcessState.ERROR);
                }

            };
//        }
        } catch (RuntimeException e) {
            // "java.io.UncheckedIOException: java.net.SocketTimeoutException: Connect timed out" if either connect or
            //  read operations timed out or if the port just wrong.
            // "java.lang.IllegalArgumentException: Failed to get address for host" if the host can't be found; DNS
            LOG.error("Failed to send: ", e);
            throw e;
        }
        //} catch (UncheckedIOException e) {
        //    LOG.warn("Unhandled IOException", e);
        //    return new ProcessStateRoutingKey(ProcessState.RETRY);
        //} catch (IllegalArgumentException e) {
        //    LOG.warn("", e);
        //    return new ProcessStateRoutingKey(ProcessState.ERROR);
        //}
    }

    // NB: This is a gateway specific class. Other impls might need the message, too. AWS, for example, hashes the whole request.
    public String authToken(Message message, RouteInfo routeInfo) {
        return "Bearer" + routeInfo.authorization(); // FIXME add decoding here or decode in RouteInfo constructor?
    }

    public CreateMessageRequest toGatewayMessage(Message message, RouteInfo routeInfo) {
        var cmr = new CreateMessageRequest();
        cmr.setFrom(message.from());
        cmr.setMessagingProfileId(KnownData.TELNYX_MESSAGING_PROFILE_IDS[0]);
        cmr.setTo(message.to());
        cmr.setText(message.text());
        cmr.setType(CreateMessageRequest.TypeEnum.SMS);

        return cmr;
    }

    static final Integer DEFAULT_WAIT_TEN_SECONDS = 10; // This is a complete guess.

    RetryDelayRoutingKey routingKeyForDelay(ClientResponseHeaders headers) {
        final int delay = headers.find(RETRY_AFTER)
                .or(() -> headers.find(RATE_LIMIT_RESET))
                .map(Header::get)
                .flatMap(this::toIntOptional)
                .orElse(DEFAULT_WAIT_TEN_SECONDS);
        return convert(delay);
    }

    Optional<Integer> toIntOptional(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Fit the specified delay to available retry key/queues. Picks the shortest delay that is **at least**
     * as long as what Telnyx specified. This means we may delay longer than required, but we don't want to balloon the
     * number of queues we maintain.
     * @param delaySeconds the time Telnyx directed us to wait or when the limit resets.
     * @return the routing key used to pick the correct retry queue.
     */
    RetryDelayRoutingKey convert(Integer delaySeconds) {
        long delayMillis = delaySeconds * 1_000L;
        for(RetryDelayRoutingKey routingKey : RetryDelayRoutingKey.values()) {
            if (delayMillis <= routingKey.delayMs()) {
                return routingKey;
            }
        }
        LOG.warn("Telnyx specified delay {} sec exceeds available delay queue. Using DELAY_20M", delaySeconds);
        return RetryDelayRoutingKey.DELAY_20M; // Max delay
    }

}
