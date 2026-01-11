package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.CustomerStatus;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

public class CustomerRow {
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
