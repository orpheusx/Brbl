package com.enoughisasgoodasafeast.operator;

import java.time.Instant;
import java.util.UUID;

public record Route(UUID id,
                    Platform platform,
                    String channel,
                    UUID default_node_id,
                    UUID customer_id,
                    RouteStatus status,
                    Instant created_at,
                    Instant updated_at) {

    public Route(Platform platform, String channel, UUID default_node_id, UUID customer_id) {
        this(UUID.randomUUID(), platform, channel, default_node_id, customer_id, RouteStatus.REQUESTED, Instant.now(), Instant.now());
    }
}
