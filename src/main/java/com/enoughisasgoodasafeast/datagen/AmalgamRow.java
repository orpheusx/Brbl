package com.enoughisasgoodasafeast.datagen;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

public class AmalgamRow {
    UUID groupId;
    UUID userId;
    UUID profileId ;
    UUID customerId;
    Instant createdAt ;
    Instant updatedAt;

    public AmalgamRow(UUID groupId, UUID userId, UUID profileId, UUID customerId, Instant createdAt, Instant updatedAt) {
        this.groupId = groupId;
        this.userId = userId;
        this.profileId = profileId;
        this.customerId = customerId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AmalgamRow.class.getSimpleName() + "[", "]")
                .add("groupId=" + groupId)
                .add("userId=" + userId)
                .add("profileId=" + profileId)
                .add("customerId=" + customerId)
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .toString();
    }
}
