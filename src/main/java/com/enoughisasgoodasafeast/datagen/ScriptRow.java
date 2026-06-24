package com.enoughisasgoodasafeast.datagen;

//                        Table "brbl_logic.scripts"
//   Column    |           Type           | Collation | Nullable | Default
//-------------+--------------------------+-----------+----------+---------
// id          | uuid                     |           | not null |
// name        | character varying(64)    |           | not null |
// description | character varying(128)   |           |          |
// company_id | uuid                     |           |          |
// node_id     | uuid                     |           |          |
// status      | script_status            |           |          |
// language    | public.language_code     |           |          |
// created_at  | timestamp with time zone |           | not null |
// updated_at  | timestamp with time zone |           | not null |

import com.enoughisasgoodasafeast.operator.LanguageCode;
import com.enoughisasgoodasafeast.operator.ScriptStatus;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static java.time.Instant.now;

public class ScriptRow implements BrblRow {

    public static final String INSERT_SQL = """
            INSERT INTO brbl_logic.scripts
                (id, name, description, company_id, node_id, status, language, created_at, updated_at)
                VALUES
            """;

    public static final String VALUES_SQL = """
            ('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s'),
            """;

    UUID id;
    String name;
    String description;
    UUID companyId;
    UUID nodeId;
    ScriptStatus status;
    LanguageCode language;
    Instant createdAt;
    Instant updatedAt;

    public ScriptRow(UUID id, String name, String description, UUID companyId, UUID nodeId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.companyId = companyId;
        this.nodeId = nodeId;
        this.status = ScriptStatus.PROD;
        this.language = LanguageCode.ENG;
        this.createdAt = now();
        this.updatedAt = now();
    }

    public ScriptRow(String name, String description, UUID companyId, UUID nodeId) {
        this.id = randomUUID();
        this.name = name;
        this.description = description;
        this.companyId = companyId;
        this.nodeId = nodeId;
        this.status = ScriptStatus.PROD;
        this.language = LanguageCode.ENG;
        this.createdAt = now();
        this.updatedAt = now();
    }

    String getValuesSql() {
        return String.format(VALUES_SQL,
                id, name, description, companyId, nodeId, status, language, createdAt, updatedAt);
    }

    public String[] headers() {
        return new String[] {
                "id",
                "name",
                "description",
                "node_id",
                "status",
                "language",
                "created_at",
                "updated_at",
                "company_id"
        };
    }

    public String[] values() {
        return new String[]{
                id.toString(), name, description, nodeId.toString(),
                status.name(), language.name(), createdAt.toString(), updatedAt.toString(),
                companyId.toString()};
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ScriptRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("name='" + name + "'")
                .add("description='" + description + "'")
                .add("companyId=" + companyId)
                .add("nodeId=" + nodeId)
                .add("status=" + status)
                .add("language=" + language)
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .toString();
    }
}
