package com.enoughisasgoodasafeast.operator;

import java.time.Instant;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static io.jenetics.util.NanoClock.*;

/*
                            Table "brbl_users.profiles"
        Column      |           Type           | Collation | Nullable | Default
    ----------------+--------------------------+-----------+----------+---------
    id              | uuid                     |           | not null |
    surname         | character varying(36)    |           |          |
    given_name      | character varying(36)    |           |          |
    other_languages | character varying(23)    |           |          |
    created_at      | timestamp with time zone |           | not null |
    roles           | character varying(64)    |           |          |
       Indexes:
           "profiles_pkey" PRIMARY KEY, btree (group_id)
       Referenced by:
           TABLE "customers" CONSTRAINT "fk_customer_profile_group_id" FOREIGN KEY (profile_id) REFERENCES profiles(group_id)
 */

/**
 * The record class produced from rows in the PROFILES table.
 * NB: We don't include the roles column here. It's only used by the brbl-admin web app.
 * @param id
 * @param surname
 * @param givenName
 * @param otherLanguages
 * @param createdAt
 * @param updatedAt
 */
public record Profile(UUID id, String surname, String givenName, String otherLanguages, Instant createdAt, Instant updatedAt) {

    public Profile(String surname, String givenName, String otherLanguages) {
        var now = utcInstant();
        this(randomUUID(), surname, givenName, otherLanguages, now, now);
    }
}

