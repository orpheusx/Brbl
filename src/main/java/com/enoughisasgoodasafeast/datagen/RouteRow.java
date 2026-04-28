package com.enoughisasgoodasafeast.datagen;

//                                    Table "brbl_logic.routes"
//     Column      |           Type           | Collation | Nullable |          Default
// ----------------+--------------------------+-----------+----------+---------------------------
// id              | uuid                     |           | not null |
// platform        | public.platform          |           | not null |
// channel         | character varying(15)    |           | not null |
// default_node_id | uuid                     |           | not null |
// company_id     | uuid                     |           | not null |
// status          | route_status             |           | not null | 'REQUESTED'::route_status
// created_at      | timestamp with time zone |           | not null |
// updated_at      | timestamp with time zone |           | not null |

import com.enoughisasgoodasafeast.operator.CountryCode;
import com.enoughisasgoodasafeast.operator.Platform;
import com.enoughisasgoodasafeast.operator.RouteStatus;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static io.jenetics.util.NanoClock.*;

public class RouteRow implements BrblRow {

    public static final String INSERT_SQL = """
            INSERT INTO brbl_logic.routes
                (id, platform, channel, default_node_id, company_id, status, created_at, updated_at)
                VALUES
            """;

    public static final String[] headers = {"id", "platform", "channel", "default_node_id", "company_id", "status", "created_at", "updated_at"};

    public String[] headers() {
        return headers;
    }

    public static final String VALUES_SQL = """
            ('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s'),
            """;

    UUID id;
    Platform platform;
    String channel;
    UUID defaultNodeId;
    UUID companyId;
    RouteStatus status;
    Instant createdAt;
    Instant updatedAt;

    public RouteRow(String channel, UUID defaultNodeId, UUID companyId) {
        this.id = randomUUID();
        this.platform = Platform.SMS;
        this.channel = Functions.adjustPlatformId(CountryCode.US, channel);
        this.defaultNodeId = defaultNodeId;
        this.companyId = companyId;
        this.status = RouteStatus.ACTIVE;
        this.createdAt = utcInstant();
        this.updatedAt = utcInstant();
    }

    public RouteRow(UUID id, String channel, UUID defaultNodeId, Platform platform, UUID companyId) {
        this.id = id;
        this.platform = platform;
        this.channel = Functions.adjustPlatformId(CountryCode.US, channel);
        this.defaultNodeId = defaultNodeId;
        this.companyId = companyId;
        this.status = RouteStatus.ACTIVE;
        this.createdAt = utcInstant();
        this.updatedAt = utcInstant();
    }

    String getValuesSql() {
        return String.format(VALUES_SQL,
                id, platform.code(), channel, defaultNodeId, companyId, status, createdAt, updatedAt);
    }

    public String[] values() {
        return new String[]{id.toString(), platform.code(), channel, defaultNodeId.toString(),
                companyId.toString(), status.name(), createdAt.toString(), updatedAt.toString()
        };
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RouteRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("platform=" + platform)
                .add("channel='" + channel + "'")
                .add("defaultNodeId=" + defaultNodeId)
                .add("companyId=" + companyId)
                .add("status=" + status)
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .toString();
    }
}
