package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.CompanyStatus;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

public class CompanyRow {
    // id         | uuid
    // name       | character varying(64)
    // created_at | timestamp with time zone
    // updated_at | timestamp with time zone
    // status     | company_status

    public static String[] headers = {"id", "name", "created_at", "updated_at", "status"};

    UUID id;
    String name;
    CompanyStatus status;
    Instant createdAt;
    Instant updatedAt;

    public CompanyRow(UUID id, String name, CompanyStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String[] values() {
        return new String[]{id.toString(), name, createdAt.toString(), updatedAt.toString(), status.name()};
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CompanyRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("name='" + name + "'")
                .add("status=" + status)
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .toString();
    }
}
