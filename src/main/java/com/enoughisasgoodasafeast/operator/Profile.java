package com.enoughisasgoodasafeast.operator;

import java.io.IO;
import java.time.Instant;
import java.util.UUID;

/*
                            Table "brbl_users.profiles"
        Column      |           Type           | Collation | Nullable | Default
    ----------------+--------------------------+-----------+----------+---------
    group_id        | uuid                     |           | not null |
    surname         | character varying(36)    |           |          |
    given_name      | character varying(36)    |           |          |
    other_languages | character varying(23)    |           |          |
    created_at      | timestamp with time zone |           | not null |
       Indexes:
           "profiles_pkey" PRIMARY KEY, btree (group_id)
       Referenced by:
           TABLE "customers" CONSTRAINT "fk_customer_profile_group_id" FOREIGN KEY (profile_id) REFERENCES profiles(group_id)
 */
public record Profile(UUID groupId, String surname, String givenName, String otherLanguages, Instant createdAt, Instant updatedAt) {

    public Profile(String surname, String givenName, String otherLanguages) {
        var now = Instant.now();
        this(UUID.randomUUID(), surname, givenName, otherLanguages, now, now);
    }
}

