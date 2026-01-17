package com.enoughisasgoodasafeast.operator;


import java.time.Instant;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static io.jenetics.util.NanoClock.*;

public record Route(UUID id,
                    Platform platform,
                    String channel,
                    UUID default_node_id,
                    UUID customer_id,
                    RouteStatus status,
                    Instant created_at,
                    Instant updated_at) {

    public Route(Platform platform, String channel, UUID default_node_id, UUID customer_id) {
        this(randomUUID(), platform, channel, default_node_id, customer_id, RouteStatus.REQUESTED, utcInstant(), utcInstant());
    }
}
