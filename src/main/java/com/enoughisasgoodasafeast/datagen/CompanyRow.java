package com.enoughisasgoodasafeast.datagen;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

public class CompanyRow {
    //id
    //name
    //created_at
    //updated_at

    UUID id;
    String name;
    Instant createdAt;
    Instant updatedAt;

    public CompanyRow(UUID id, String name, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CompanyRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("name='" + name + "'")
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .toString();
    }
}
