package com.enoughisasgoodasafeast.sndr;

import com.enoughisasgoodasafeast.sndr.GatewayProvider;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;

/**
 * Provides the information needed to send an MT through a gateway provider.
 * If we need to track changes over time, add a trigger and descriptive table
 * à la brbl_history.user_history.
 * @param id
 * @param gatewayProvider
 * @param provider_id
 * @param authorization
 * @param rateLimitExpression
 * @param routeId
 * @param mtExpirationMs
 * @param mtRetryLimit
 * @param createdAt
 * @param updatedAt
 */
public record RouteInfo(@NonNull UUID id,
                        @NonNull GatewayProvider gatewayProvider,
                        @NonNull String provider_id,
                        @NonNull String authorization,
                        @NonNull String rateLimitExpression,
                        @NonNull UUID routeId,
                        @NonNull String channel,
                        int mtExpirationMs,
                        int mtRetryLimit,
                        @NonNull Instant createdAt,
                        @NonNull Instant updatedAt) {

    public static final int DEFAULT_EXPIRATION_MS = 28800000;
    public static final int DEFAULT_RETRY_LIMIT = 20;

    public RouteInfo(@NonNull GatewayProvider gatewayProvider, @NonNull String provider_id,
                     @NonNull String authorization, @NonNull String rateLimitExpression,
                     @NonNull UUID routeId, @NonNull String channel) {
        var now = Instant.now();
        this(randomUUID(), gatewayProvider, provider_id, authorization, rateLimitExpression, routeId, channel,
                DEFAULT_EXPIRATION_MS, DEFAULT_RETRY_LIMIT, now, now);
    }

    // Initially we only have the one provider. This is the most fully defaulted version.
    public RouteInfo(@NonNull String provider_id, @NonNull String authorization, @NonNull UUID routeId, @NonNull String channel) {
        var now = Instant.now();
        this(randomUUID(), GatewayProvider.TELNYX, provider_id, authorization, "35|35|I", routeId, channel,
                DEFAULT_EXPIRATION_MS, DEFAULT_RETRY_LIMIT, now, now);
    }

}
