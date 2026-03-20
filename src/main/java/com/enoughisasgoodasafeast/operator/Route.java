package com.enoughisasgoodasafeast.operator;


import java.time.Instant;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static io.jenetics.util.NanoClock.*;

public record Route(UUID id,
                    Platform platform,
                    String channel,
                    UUID defaultNodeId,
                    UUID companyId,
                    RouteStatus status,
                    Instant createdAt,
                    Instant updatedAt) {

    public Route(Platform platform, String channel, UUID defaultNodeId, UUID companyId) {
        this(randomUUID(), platform, channel, defaultNodeId, companyId, RouteStatus.REQUESTED, utcInstant(), utcInstant());
    }
}
