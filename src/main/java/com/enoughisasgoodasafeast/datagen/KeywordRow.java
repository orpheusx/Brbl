package com.enoughisasgoodasafeast.datagen;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

public class KeywordRow implements BrblRow {

    UUID id;
    String pattern;
    UUID scriptId;
    Instant createdAt;
    Instant updatedAt;
    UUID routeId;

    public KeywordRow(UUID id, String pattern, UUID scriptId, Instant createdAt, Instant updatedAt, UUID routeId) {
        this.id = id;
        this.pattern = pattern;
        this.scriptId = scriptId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.routeId = routeId;
    }

    public String[] headers() {
        return new String[]{"id", "pattern", "script_id", "created_at", "updated_at", "route_id"};
    }

    public String[] values() {
        return new String[]{id.toString(), pattern, scriptId.toString(), createdAt.toString(), updatedAt.toString(), routeId.toString()};
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", KeywordRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("pattern='" + pattern + "'")
                .add("scriptId=" + scriptId)
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .add("routeId=" + routeId)
                .toString();
    }
}
