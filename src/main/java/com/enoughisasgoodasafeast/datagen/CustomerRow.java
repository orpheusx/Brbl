package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.CustomerStatus;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

public class CustomerRow implements BrblRow {
    // id                | uuid
    // created_at        | timestamp with time zone
    // updated_at        | timestamp with time zone
    // email             | character varying(64)
    // company_id        | uuid
    // status            | customer_status
    // confirmation_code | character varying(12)
    // password          | character varying(72)

    // Leaving out confirmation_code and password (for now) since they are only used by brbl_admin
    public static final String[] headers = {"id", "created_at", "updated_at", "email", "company_id", "status", "confirmation_code"};

    UUID id;
    Instant createdAt;
    Instant updatedAt;
    String email;
    UUID companyId;
    CustomerStatus status;
    // 12 char max
    String confirmationCode;
    // max 72 chars
    String password;

    public CustomerRow(UUID id, Instant createdAt, Instant updatedAt, String email, UUID companyId, CustomerStatus status, String confirmationCode, String password) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.email = email;
        this.companyId = companyId;
        this.status = status;
        this.confirmationCode = confirmationCode;
        this.password = password;
    }

    public String[] headers() {
        return headers;
    }
    public String[] values() {
        return new String[]{id.toString(), createdAt.toString(), updatedAt.toString(), email, companyId.toString(), status.name(), confirmationCode};
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CustomerRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .add("email='" + email + "'")
                .add("companyId=" + companyId)
                .add("status=" + status)
                .add("confirmationCode='" + confirmationCode + "'")
                .add("password='" + password + "'")
                .toString();
    }
}
