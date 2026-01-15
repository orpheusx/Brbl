package com.enoughisasgoodasafeast.datagen;

//                                    Table "brbl_logic.routes"
//     Column      |           Type           | Collation | Nullable |          Default
// ----------------+--------------------------+-----------+----------+---------------------------
// id              | uuid                     |           | not null |
// platform        | public.platform          |           | not null |
// channel         | character varying(15)    |           | not null |
// default_node_id | uuid                     |           | not null |
// customer_id     | uuid                     |           | not null |
// status          | route_status             |           | not null | 'REQUESTED'::route_status
// created_at      | timestamp with time zone |           | not null |
// updated_at      | timestamp with time zone |           | not null |

import com.enoughisasgoodasafeast.operator.CountryCode;
import com.enoughisasgoodasafeast.operator.Platform;
import com.enoughisasgoodasafeast.operator.RouteStatus;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

public class RouteRow {

    public static final String INSERT_SQL = """
            INSERT INTO brbl_logic.routes
                (id, platform, channel, default_node_id, customer_id, status, created_at, updated_at)
                VALUES
            """;

    public static final String VALUES_SQL = """
            ('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s'),
            """;

    UUID id;
    Platform platform;
    String channel;
    UUID defaultNodeId;
    UUID customerId;
    RouteStatus status;
    Instant createdAt;
    Instant updatedAt;

    public RouteRow(UUID id, Platform platform, String channel, UUID defaultNodeId, UUID customerId, RouteStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.platform = platform;
        this.channel = channel;
        this.defaultNodeId = defaultNodeId;
        this.customerId = customerId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public RouteRow(String channel, UUID defaultNodeId, UUID customerId) {
        this.id = UUID.randomUUID();
        this.platform = Platform.SMS;
        this.channel = Functions.adjustPlatformId(CountryCode.US, channel);
        this.defaultNodeId = defaultNodeId;
        this.customerId = customerId;
        this.status = RouteStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    String getValuesSql() {
        return String.format(VALUES_SQL,
                id, platform.code(), channel, defaultNodeId, customerId, status, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RouteRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("platform=" + platform)
                .add("channel='" + channel + "'")
                .add("defaultNodeId=" + defaultNodeId)
                .add("customerId=" + customerId)
                .add("status=" + status)
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .toString();
    }
}
