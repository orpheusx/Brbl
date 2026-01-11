package com.enoughisasgoodasafeast.datagen;

import java.time.Instant;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

import static com.enoughisasgoodasafeast.operator.NodeType.PresentMulti;
import static java.lang.Math.min;

public class NodeRow {

    // id         | uuid
    // created_at | timestamp with time zone
    // text       | character varying(255)
    // type       | smallint
    // label      | character varying(32)
    // updated_at | timestamp with time zone

    public static final String INSERT_SQL = """
            INSERT INTO brbl_logic.nodes
                (id, created_at, text, type, label, updated_at)
                VALUES
            """;

    public static final String VALUES_SQL = """
            ('%s', '%s', '%s', %d, '%s', '%s'),
            """;

    UUID id;
    Instant createdAt;
    String text;
    int type;
    String label;
    Instant updatedAt;

    public NodeRow(String text) {
        var ctext = text.replaceAll("'", "''");
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.text = ctext;
        this.type = PresentMulti.ordinal();
        int start = min(7, ctext.length());
        //int len = Math.min(10, start + text.length());
        this.label = ctext.substring(start, min(text.length(), 32));
        if(label.endsWith("'")) label = label.substring(0, label.length() - 1);
        this.updatedAt = this.createdAt;
    }

    String getValuesSql() {
        return String.format(VALUES_SQL,
                id, createdAt, text, type, label, updatedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NodeRow nodeRow)) return false;
        return Objects.equals(text, nodeRow.text);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(text);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", NodeRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("createdAt=" + createdAt)
                .add("text='" + text + "'")
                .add("label='" + label + "'")
                .add("updatedAt=" + updatedAt)
                .toString();
    }
}
