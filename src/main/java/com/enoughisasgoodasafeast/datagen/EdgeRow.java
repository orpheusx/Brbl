package com.enoughisasgoodasafeast.datagen;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static io.jenetics.util.NanoClock.utcInstant;

public class EdgeRow {
    //id            | uuid
    //created_at    | timestamp with time zone
    //match_text    | character varying(128)
    //response_text | character varying(255)
    //src           | uuid
    //dst           | uuid
    //updated_at    | timestamp with time zone
    public static final String INSERT_SQL = """
            INSERT INTO brbl_logic.edges
                (id, created_at, match_text, response_text, src, dst, updated_at)
                VALUES
            """;

    public static final String VALUES_SQL = """
            ('%s', '%s', '%s', '%s', '%s', %s, '%s'),
            """;

    UUID id;
    Instant createdAt;
    String matchText;
    String responseText;
    UUID src;
    UUID dst;
    Instant updatedAt;

    public EdgeRow(String responseText) {
        responseText = responseText.replace("'", "''");
        this.id = randomUUID();
        this.createdAt = utcInstant();
        this.matchText = responseText.substring(0, Math.min(responseText.length(), 10));
        this.responseText = responseText;
        this.updatedAt = this.createdAt;
    }

    String getValuesSql() {
        return String.format(VALUES_SQL,
                id, createdAt, matchText, responseText, src, dst, updatedAt);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", EdgeRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("createdAt=" + createdAt)
                .add("matchText='" + matchText + "'")
                .add("responseText='" + responseText + "'")
                .add("src=" + src)
                .add("dst=" + dst)
                .add("updatedAt=" + updatedAt)
                .toString();
    }
}
