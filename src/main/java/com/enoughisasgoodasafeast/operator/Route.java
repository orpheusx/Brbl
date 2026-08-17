package com.enoughisasgoodasafeast.operator;


import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static java.time.Instant.now;

public record Route(@NonNull UUID id,
                    @NonNull Platform platform,
                    @NonNull String channel,
                    @NonNull UUID defaultNodeId,
                    @NonNull UUID companyId,
                    @NonNull RouteStatus status,
                    @NonNull UUID interruptNodeId,
                    @NonNull UUID optInNodeId,
                    @NonNull UUID optOutNodeId,
                    @NonNull Instant createdAt,
                    @NonNull Instant updatedAt) {

    public Route(Platform platform, String channel, UUID defaultNodeId, UUID companyId, UUID interruptNodeId, UUID optInNodeId, UUID optOutNodeId) {
        this(randomUUID(), platform, channel, defaultNodeId, companyId, RouteStatus.REQUESTED, interruptNodeId,
                optInNodeId, optOutNodeId, now(), now());
    }
}
