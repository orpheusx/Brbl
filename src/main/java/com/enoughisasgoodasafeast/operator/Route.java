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

    public static final int DEFAULT_EXPIRATION_MS =  28800000;
    public static final int DEFAULT_RETRY_LIMIT =  20;

//    public Route(@NonNull UUID id, @NonNull Platform platform, @NonNull String channel, @NonNull UUID defaultNodeId,
//                 @NonNull UUID companyId, @NonNull RouteStatus status, @NonNull UUID interruptNodeId,
//                 @NonNull UUID optInNodeId, @NonNull UUID optOutNodeId,
//                 @NonNull Instant createdAt, @NonNull Instant updatedAt) {
//
//        this(id, platform, channel, defaultNodeId, companyId, status,
//                interruptNodeId, optInNodeId, optOutNodeId,
//                createdAt, updatedAt);
//    }

//    public Route(@NonNull UUID id, @NonNull Platform platform, @NonNull String channel, @NonNull UUID defaultNodeId,
//                 @NonNull UUID companyId, @NonNull RouteStatus status, @NonNull UUID interruptNodeId,
//                 @NonNull UUID optInNodeId, @NonNull UUID optOutNodeId, @NonNull Instant createdAt, @NonNull Instant updatedAt) {
//
//        this.id = id;
//        this.platform = platform;
//        this.channel = channel;
//        this.defaultNodeId = defaultNodeId;
//        this.companyId = companyId;
//        this.status = status;
//        this.interruptNodeId = interruptNodeId;
//        this.optInNodeId = optInNodeId;
//        this.optOutNodeId = optOutNodeId;
//        this.mtExpirationMs = mtExpirationMs;
//        this.mtRetryLimit = mtRetryLimit;
//        this.createdAt = createdAt;
//        this.updatedAt = updatedAt;
//    }

    public Route(@NonNull Platform platform, @NonNull String channel, @NonNull UUID defaultNodeId,
                 @NonNull UUID companyId, @NonNull UUID interruptNodeId, @NonNull UUID optInNodeId,
                 @NonNull UUID optOutNodeId) {

        this(randomUUID(), platform, channel, defaultNodeId, companyId, RouteStatus.REQUESTED, interruptNodeId,
                optInNodeId, optOutNodeId, now(), now());
    }
}
