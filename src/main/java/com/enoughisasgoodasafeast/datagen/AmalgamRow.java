package com.enoughisasgoodasafeast.datagen;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

public class AmalgamRow {
    // group_id    | uuid
    // user_id     | uuid
    // profile_id  | uuid
    // customer_id | uuid
    // created_at  | timestamp with time zone
    // updated_at  | timestamp with time zone
    // claimant_id | uuid

    public static final String[] headers = {"group_id", "user_id", "profile_id", "customer_id", "created_at", "updated_at", "claimant_id"};

    UUID groupId;
    UUID userId;
    UUID profileId;
    UUID customerId;
    Instant createdAt;
    Instant updatedAt;
    UUID claimantId;

    public AmalgamRow(UUID groupId, UUID userId, UUID profileId, UUID customerId, Instant createdAt, Instant updatedAt, UUID claimantId) {
        this.groupId = groupId;
        this.userId = userId;
        this.profileId = profileId;
        this.customerId = customerId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.claimantId = claimantId;
    }

    public String[] headers() {
        return headers;
    }

    public String[] values() {
        return new String[]{groupId.toString(), userId.toString(),
                (null == profileId)  ? "" : profileId.toString(),
                (null == customerId) ? "" : customerId.toString(),
                createdAt.toString(), updatedAt.toString(), claimantId.toString()};
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
                .add("claimantId=" + claimantId)
                .toString();
    }
}
