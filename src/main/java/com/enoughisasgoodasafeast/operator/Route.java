package com.enoughisasgoodasafeast.operator;


import java.time.Instant;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static java.time.Instant.now;

public record Route(UUID id,
                    Platform platform,
                    String channel,
                    UUID defaultNodeId,
                    UUID companyId,
                    RouteStatus status,
                    UUID interruptNodeId,
                    UUID optInNodeId,
                    UUID optOutNodeId,
                    Instant createdAt,
                    Instant updatedAt) {

    public Route(Platform platform, String channel, UUID defaultNodeId, UUID companyId, UUID interruptNodeId, UUID optInNodeId, UUID optOutNodeId) {
        this(randomUUID(), platform, channel, defaultNodeId, companyId, RouteStatus.REQUESTED, interruptNodeId,
                optInNodeId, optOutNodeId, now(), now());
    }
}
